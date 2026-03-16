#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${DATABASE_URL:-}" ]]; then
  echo "DATABASE_URL is required."
  exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required."
  exit 1
fi

MODE="${MODE:-}"
if [[ "$MODE" != "select-only" && "$MODE" != "full-path" ]]; then
  echo "MODE is required and must be one of: select-only | full-path"
  exit 1
fi

EXPERIMENT_KIND="${EXPERIMENT_KIND:-capacity}" # capacity | stress
if [[ "$EXPERIMENT_KIND" != "capacity" && "$EXPERIMENT_KIND" != "stress" ]]; then
  echo "EXPERIMENT_KIND must be one of: capacity | stress"
  exit 1
fi

BASE_URL="${BASE_URL:-http://localhost:8080}"
SHORT_CODE="${SHORT_CODE:-code000001}"
EXPECTED_LOCATION="${EXPECTED_LOCATION:-https://example.com/000001}"
TEST_DURATION="${TEST_DURATION:-45s}"
PRE_ALLOCATED_VUS="${PRE_ALLOCATED_VUS:-100}"
MAX_VUS="${MAX_VUS:-1200}"
RATE_LIST="${RATE_LIST:-50 75 100 125 150}"
RESET_EACH_RUN="${RESET_EACH_RUN:-true}"

# experiment controls
WARMUP_STRATEGY="${WARMUP_STRATEGY:-batch_once}" # batch_once | per_run_short | off
WARMUP_RPS="${WARMUP_RPS:-30}"
WARMUP_DURATION="${WARMUP_DURATION:-15s}"
COOLDOWN_SECONDS="${COOLDOWN_SECONDS:-15}"
STOP_ON_FAILURE="${STOP_ON_FAILURE:-true}"
CONTINUE_ON_K6_FAILURE="${CONTINUE_ON_K6_FAILURE:-false}"

HEALTH_GATE_ENABLED="${HEALTH_GATE_ENABLED:-true}"
HEALTH_CHECK_RETRIES="${HEALTH_CHECK_RETRIES:-12}"
HEALTH_CHECK_INTERVAL_SECONDS="${HEALTH_CHECK_INTERVAL_SECONDS:-5}"
HEALTH_MAX_ACTIVE_CONNECTIONS="${HEALTH_MAX_ACTIVE_CONNECTIONS:-5}"

# fine-step retest (capacity mode only)
REFINE_ON_FAIL="${REFINE_ON_FAIL:-true}"
REFINE_STEP="${REFINE_STEP:-25}"

# SLO gate for capacity judgement
SLO_P95_MS="${SLO_P95_MS:-200}"

# validation / redirect-check controls passed through to k6 JS
DISABLE_THRESHOLDS="${DISABLE_THRESHOLDS:-false}"
LOCATION_MATCH_MODE="${LOCATION_MATCH_MODE:-exact}"

STAMP="$(date +%Y%m%d-%H%M%S)"
RUN_ROOT="${OUT_DIR:-backend/perf/out/hot-capacity-compare}/${STAMP}/${MODE}-${EXPERIMENT_KIND}"
mkdir -p "$RUN_ROOT"
RESULT_CSV="$RUN_ROOT/comparison.csv"
SUMMARY_MD="$RUN_ROOT/summary.md"
SUMMARY_JSON="$RUN_ROOT/summary.json"

echo "mode,experiment_kind,phase,target_rps,achieved_rps,http_reqs,dropped_iterations,http_req_failed_rate,p95_ms,p99_ms,vus_max,select_calls,insert_calls,update_calls,write_calls,collapse,k6_exit_code,sample_type,health_gate_before,health_gate_after,contamination_reason,metrics_parse_ok,db_snapshot_ok,judgement_basis" > "$RESULT_CSV"

get_runner_and_script() {
  if [[ "$MODE" == "select-only" ]]; then
    echo "backend/perf/run-select-only-with-monitoring.sh backend/perf/select-only-redirect-hot.js /s-select/${SHORT_CODE}"
  else
    echo "backend/perf/run-redirect-with-monitoring.sh backend/perf/redirect-load-test.js /s/${SHORT_CODE}"
  fi
}

read -r RUNNER_PATH K6_SCRIPT HEALTH_PATH <<< "$(get_runner_and_script)"

check_http_gate() {
  local code
  code="$(curl -sS -o /dev/null -w '%{http_code}' -L --max-redirs 0 "${BASE_URL}${HEALTH_PATH}" || true)"
  [[ "$code" == "302" || "$code" == "301" ]]
}

check_db_gate() {
  local active
  active="$(psql "$DATABASE_URL" -At -F ' ' -c "SELECT count(*) FILTER (WHERE state = 'active') FROM pg_stat_activity WHERE datname = current_database();" 2>/dev/null || true)"
  [[ -n "$active" && "$active" =~ ^[0-9]+$ ]] || return 1
  (( active <= HEALTH_MAX_ACTIVE_CONNECTIONS ))
}

wait_for_health_gate() {
  if [[ "${HEALTH_GATE_ENABLED,,}" != "true" ]]; then
    echo "yes"
    return 0
  fi

  for ((attempt=1; attempt<=HEALTH_CHECK_RETRIES; attempt++)); do
    if check_http_gate && check_db_gate; then
      echo "yes"
      return 0
    fi
    sleep "$HEALTH_CHECK_INTERVAL_SECONDS"
  done

  echo "no"
  return 1
}

get_json_metric() {
  local json_file="$1"
  local jq_expr="$2"
  local default_value="$3"

  local value
  value="$(jq -r "$jq_expr // empty" "$json_file" 2>/dev/null || true)"
  if [[ -n "$value" && "$value" != "null" ]]; then
    echo "$value"
  else
    echo "$default_value"
  fi
}

extract_k6_metrics() {
  local json_file="$1"

  # required metrics: default to empty string so parse success can be validated
  achieved_rps="$(get_json_metric "$json_file" '.metrics.http_reqs.rate' '')"
  http_reqs="$(get_json_metric "$json_file" '.metrics.http_reqs.count' '')"
  failed_rate="$(get_json_metric "$json_file" '.metrics.http_req_failed.value' '')"
  p95_ms="$(get_json_metric "$json_file" '.metrics.http_req_duration["p(95)"]' '')"
  p99_ms="$(get_json_metric "$json_file" '.metrics.http_req_duration["p(99)"]' '')"

  # optional metrics
  dropped_count="$(get_json_metric "$json_file" '.metrics.dropped_iterations.count' '0')"
  vus_max="$(get_json_metric "$json_file" '.metrics.vus_max.value' '0')"
}

safe_metric_from_db_snapshot() {
  local file="$1"
  local key="$2"

  awk -F',' -v k="$key" '$1==k {print $2; found=1} END { if (!found) print 0 }' "$file"
}

run_warmup_if_needed() {
  local strategy="$1"
  local context="$2"

  case "$strategy" in
    off)
      return 0
      ;;
    batch_once|per_run_short)
      echo "[warmup][$context] ${WARMUP_RPS} rps for ${WARMUP_DURATION}"
      OUT_DIR="$RUN_ROOT/warmup/${context}" \
      BASE_URL="$BASE_URL" \
      SHORT_CODE="$SHORT_CODE" \
      EXPECTED_LOCATION="$EXPECTED_LOCATION" \
      TARGET_RPS="$WARMUP_RPS" \
      TEST_DURATION="$WARMUP_DURATION" \
      PRE_ALLOCATED_VUS="$PRE_ALLOCATED_VUS" \
      MAX_VUS="$MAX_VUS" \
      RESET_PG_STAT_STATEMENTS="false" \
      DISABLE_THRESHOLDS="$DISABLE_THRESHOLDS" \
      LOCATION_MATCH_MODE="$LOCATION_MATCH_MODE" \
      "$RUNNER_PATH" "$K6_SCRIPT" >/dev/null 2>&1 || true
      ;;
    *)
      echo "Unsupported WARMUP_STRATEGY=$strategy"
      exit 1
      ;;
  esac
}

record_row() {
  echo "$1,$2,$3,$4,$5,$6,$7,$8,$9,${10},${11},${12},${13},${14},${15},${16},${17},${18},${19},${20},${21},${22},${23},${24}" >> "$RESULT_CSV"
}

run_one() {
  local phase="$1"
  local target_rps="$2"

  if [[ "$WARMUP_STRATEGY" == "per_run_short" ]]; then
    run_warmup_if_needed "per_run_short" "${phase}-${target_rps}"
  fi

  local health_before="yes"
  if ! health_before="$(wait_for_health_gate)"; then
    record_row \
      "$MODE" "$EXPERIMENT_KIND" "$phase" "$target_rps" \
      0 0 0 0 0 0 0 0 0 0 0 \
      "yes" 98 "contaminated" "$health_before" "no" "health_gate_before_failed" \
      "no" "no" "health_gate_before_only"
    return 98
  fi

  local scenario_out="$RUN_ROOT/${phase}/rps-${target_rps}"
  mkdir -p "$scenario_out"

  local scenario_summary_json="$scenario_out/k6-summary.json"
  local run_log="$scenario_out/run.log"
  local k6_exit=0

  echo ""
  echo ">>> [${MODE}] phase=${phase} target RPS=${target_rps}"

  set +e
  OUT_DIR="$scenario_out" \
  BASE_URL="$BASE_URL" \
  SHORT_CODE="$SHORT_CODE" \
  EXPECTED_LOCATION="$EXPECTED_LOCATION" \
  TARGET_RPS="$target_rps" \
  TEST_DURATION="$TEST_DURATION" \
  PRE_ALLOCATED_VUS="$PRE_ALLOCATED_VUS" \
  MAX_VUS="$MAX_VUS" \
  RESET_PG_STAT_STATEMENTS="$RESET_EACH_RUN" \
  DISABLE_THRESHOLDS="$DISABLE_THRESHOLDS" \
  LOCATION_MATCH_MODE="$LOCATION_MATCH_MODE" \
  "$RUNNER_PATH" "$K6_SCRIPT" --summary-export "$scenario_summary_json" 2>&1 | tee "$run_log"
  k6_exit=$?
  set -e

  local pre_file post_file
  pre_file="$(find "$scenario_out" -type f -name '*db-pre.txt' | head -n 1 || true)"
  post_file="$(find "$scenario_out" -type f -name '*db-post.txt' | head -n 1 || true)"

  local select_delta=0 insert_delta=0 update_delta=0
  local db_snapshot_ok="no"
  if [[ -n "$pre_file" && -n "$post_file" && -f "$pre_file" && -f "$post_file" ]]; then
    db_snapshot_ok="yes"
    select_delta="$(( $(safe_metric_from_db_snapshot "$post_file" "short_links_select_calls") - $(safe_metric_from_db_snapshot "$pre_file" "short_links_select_calls") ))"
    insert_delta="$(( $(safe_metric_from_db_snapshot "$post_file" "link_click_events_insert_calls") - $(safe_metric_from_db_snapshot "$pre_file" "link_click_events_insert_calls") ))"
    update_delta="$(( $(safe_metric_from_db_snapshot "$post_file" "short_links_total_clicks_update_calls") - $(safe_metric_from_db_snapshot "$pre_file" "short_links_total_clicks_update_calls") ))"
  fi

  local achieved_rps="" http_reqs="" dropped_count="0" failed_rate="" p95_ms="" p99_ms="" vus_max="0"
  local metrics_parse_ok="no"

  if [[ -f "$scenario_summary_json" ]]; then
    extract_k6_metrics "$scenario_summary_json"

    if [[ -n "$achieved_rps" && -n "$http_reqs" && -n "$failed_rate" && -n "$p95_ms" && -n "$p99_ms" ]]; then
      metrics_parse_ok="yes"
    fi
  fi

  achieved_rps="${achieved_rps:-0}"
  http_reqs="${http_reqs:-0}"
  dropped_count="${dropped_count:-0}"
  failed_rate="${failed_rate:-0}"
  p95_ms="${p95_ms:-0}"
  p99_ms="${p99_ms:-0}"
  vus_max="${vus_max:-0}"

  local collapse="no"
  local judgement_basis=""

  if [[ "${DISABLE_THRESHOLDS,,}" == "true" ]]; then
    collapse="no"
    judgement_basis="validation_mode"
  elif [[ "$metrics_parse_ok" != "yes" ]]; then
    collapse="yes"
    judgement_basis="metrics_missing"
  else
    collapse="$(
      awk -v achieved="$achieved_rps" \
          -v target="$target_rps" \
          -v dropped="$dropped_count" \
          -v failed="$failed_rate" \
          -v p95="$p95_ms" \
          -v slo="$SLO_P95_MS" '
      BEGIN {
        if (achieved < target * 0.95 || dropped > 0 || failed > 0 || p95 > slo) print "yes";
        else print "no";
      }'
    )"
    judgement_basis="k6json"
  fi

  if [[ "$db_snapshot_ok" == "yes" ]]; then
    judgement_basis="${judgement_basis}+dbsnapshot"
  fi

  local sample_type="capacity"
  local contamination_reason=""

  if [[ "$k6_exit" -ne 0 ]]; then
    sample_type="execution_failed"
  elif [[ "$collapse" == "yes" ]]; then
    sample_type="stress"
  fi

  sleep "$COOLDOWN_SECONDS"

  local health_after="yes"
  if ! health_after="$(wait_for_health_gate)"; then
    sample_type="contaminated"
    contamination_reason="health_gate_after_failed"
  fi

  local write_calls=$((insert_delta + update_delta))

  record_row \
    "$MODE" "$EXPERIMENT_KIND" "$phase" "$target_rps" \
    "$achieved_rps" "$http_reqs" "$dropped_count" "$failed_rate" "$p95_ms" "$p99_ms" "$vus_max" \
    "$select_delta" "$insert_delta" "$update_delta" "$write_calls" \
    "$collapse" "$k6_exit" "$sample_type" "$health_before" "$health_after" "$contamination_reason" \
    "$metrics_parse_ok" "$db_snapshot_ok" "$judgement_basis"

  if [[ "$sample_type" == "contaminated" ]]; then
    return 97
  fi

  if [[ "$k6_exit" -ne 0 && "${CONTINUE_ON_K6_FAILURE,,}" != "true" ]]; then
    return "$k6_exit"
  fi

  return 0
}

generate_summary() {
  {
    echo "# Hot path capacity comparison"
    echo ""
    echo "- mode: $MODE"
    echo "- experiment_kind: $EXPERIMENT_KIND"
    echo "- result csv: \`$RESULT_CSV\`"
    echo "- summary json: \`$SUMMARY_JSON\`"
    echo "- warmup_strategy: $WARMUP_STRATEGY"
    echo "- cooldown_seconds: $COOLDOWN_SECONDS"
    echo "- stop_on_failure: $STOP_ON_FAILURE"
    echo "- continue_on_k6_failure: $CONTINUE_ON_K6_FAILURE"
    echo "- slo_p95_ms: $SLO_P95_MS"
    echo "- disable_thresholds: $DISABLE_THRESHOLDS"
    echo "- location_match_mode: $LOCATION_MATCH_MODE"
    echo "- health_gate: $HEALTH_GATE_ENABLED (retries=$HEALTH_CHECK_RETRIES, interval=${HEALTH_CHECK_INTERVAL_SECONDS}s, max_active_conn=$HEALTH_MAX_ACTIVE_CONNECTIONS)"
    echo ""
    echo "| phase | target_rps | achieved_rps | dropped | fail_rate | p95_ms | p99_ms | vus_max | collapse | k6_exit | sample_type | metrics_parse_ok | db_snapshot_ok | judgement_basis | contamination_reason |"
    echo "|---|---:|---:|---:|---:|---:|---:|---:|---|---:|---|---|---|---|---|"
    awk -F',' 'NR>1 {printf "| %s | %s | %.2f | %s | %.6f | %.2f | %.2f | %s | %s | %s | %s | %s | %s | %s | %s |\n", $3, $4, $5+0, $7, $8+0, $9+0, $10+0, $11, $16, $17, $18, $22, $23, $24, $21 }' "$RESULT_CSV"
    echo ""
    echo "## Sample classification counts"
    echo ""
    awk -F',' 'NR>1 { c[$18]++ } END { for (k in c) printf "- %s: %d\n", k, c[k] }' "$RESULT_CSV" | sort
  } > "$SUMMARY_MD"

  jq -n \
    --arg mode "$MODE" \
    --arg experiment_kind "$EXPERIMENT_KIND" \
    --arg result_csv "$RESULT_CSV" \
    --arg summary_md "$SUMMARY_MD" \
    --arg slo_p95_ms "$SLO_P95_MS" \
    --arg disable_thresholds "$DISABLE_THRESHOLDS" \
    --arg location_match_mode "$LOCATION_MATCH_MODE" \
    '{
      mode: $mode,
      experiment_kind: $experiment_kind,
      result_csv: $result_csv,
      summary_md: $summary_md,
      slo_p95_ms: ($slo_p95_ms | tonumber),
      disable_thresholds: $disable_thresholds,
      location_match_mode: $location_match_mode
    }' > "$SUMMARY_JSON"
}

run_capacity_plan() {
  local first_fail=""
  local prev_pass=""

  if [[ "$WARMUP_STRATEGY" == "batch_once" ]]; then
    run_warmup_if_needed "batch_once" "batch-start"
  fi

  for rate in $RATE_LIST; do
    local rc=0
    run_one "staircase" "$rate" || rc=$?

    local last_row
    last_row="$(tail -n 1 "$RESULT_CSV")"

    local collapse k6_exit sample_type
    collapse="$(echo "$last_row" | awk -F',' '{print $16}')"
    k6_exit="$(echo "$last_row" | awk -F',' '{print $17}')"
    sample_type="$(echo "$last_row" | awk -F',' '{print $18}')"

    if [[ "$sample_type" == "contaminated" ]]; then
      echo "[abort] contaminated sample detected at rate=$rate"
      break
    fi

    if [[ "$collapse" == "yes" || "$k6_exit" -ne 0 ]]; then
      first_fail="$rate"
      if [[ "${STOP_ON_FAILURE,,}" == "true" ]]; then
        break
      fi
    else
      prev_pass="$rate"
    fi

    if [[ "$rc" -ne 0 && "${STOP_ON_FAILURE,,}" == "true" ]]; then
      break
    fi
  done

  if [[ "${REFINE_ON_FAIL,,}" == "true" && -n "$first_fail" && -n "$prev_pass" ]]; then
    echo "[refine] prev_pass=$prev_pass first_fail=$first_fail step=$REFINE_STEP"
    for ((r=prev_pass+REFINE_STEP; r<first_fail; r+=REFINE_STEP)); do
      run_one "fine" "$r" || true
    done
  fi
}

run_stress_plan() {
  if [[ "$WARMUP_STRATEGY" == "batch_once" ]]; then
    run_warmup_if_needed "batch_once" "batch-start"
  fi

  for rate in $RATE_LIST; do
    run_one "stress" "$rate" || true
  done
}

if [[ "$EXPERIMENT_KIND" == "capacity" ]]; then
  run_capacity_plan
else
  run_stress_plan
fi

generate_summary

echo ""
echo "Done."
echo "- comparison csv: $RESULT_CSV"
echo "- markdown summary: $SUMMARY_MD"
echo "- json summary: $SUMMARY_JSON"