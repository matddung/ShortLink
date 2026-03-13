#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <k6-script> [k6 args...]"
  echo "Examples:"
  echo "  $0 backend/perf/redirect-load-test.js"
  echo "  $0 backend/perf/redirect-miss-load-test.js"
  echo "  $0 backend/perf/redirect-random-distributed.js"
  exit 1
fi

SCRIPT_PATH="$1"
shift || true
SCRIPT_NAME="$(basename "$SCRIPT_PATH")"
SCRIPT_NAME_NO_EXT="${SCRIPT_NAME%.*}"

if [[ -z "${DATABASE_URL:-}" ]]; then
  echo "DATABASE_URL is required (for pg_stat_statements / pg_stat_activity collection)."
  exit 1
fi

OUT_DIR="${OUT_DIR:-backend/perf/out}"
RESULT_DIR="$OUT_DIR/$SCRIPT_NAME_NO_EXT"
mkdir -p "$RESULT_DIR"
STAMP="$(date +%Y%m%d-%H%M%S)"
PREFIX="$RESULT_DIR/redirect-${STAMP}"
SAMPLE_CSV="${PREFIX}-runtime-samples.csv"
PRE_METRICS="${PREFIX}-db-pre.txt"
POST_METRICS="${PREFIX}-db-post.txt"
K6_LOG="${PREFIX}-k6.log"

RESET_PG_STAT_STATEMENTS="${RESET_PG_STAT_STATEMENTS:-true}"

reset_pg_stat_statements() {
  if [[ "${RESET_PG_STAT_STATEMENTS,,}" != "true" ]]; then
    echo "Skip pg_stat_statements reset (RESET_PG_STAT_STATEMENTS=$RESET_PG_STAT_STATEMENTS)"
    return
  fi

  echo "Reset pg_stat_statements"
  psql "$DATABASE_URL" -c "SELECT pg_stat_statements_reset();" >/dev/null
}

collect_db_snapshot() {
  psql "$DATABASE_URL" -At -F',' -f backend/perf/pg-stat-redirect.sql
}

sampler() {
  echo "ts,app_cpu_percent,db_cpu_percent,db_connections_total,db_connections_active" > "$SAMPLE_CSV"

  while true; do
    ts="$(date +%s)"
    app_cpu="$(ps -C java -o %cpu= 2>/dev/null | awk '{s+=$1} END {printf "%.2f", (s==""?0:s)}' || true)"
    db_cpu="$(ps -C postgres -o %cpu= 2>/dev/null | awk '{s+=$1} END {printf "%.2f", (s==""?0:s)}' || true)"

    if [[ -z "$app_cpu" ]]; then app_cpu="0.00"; fi
    if [[ -z "$db_cpu" ]]; then db_cpu="0.00"; fi

    conn_line="$(psql "$DATABASE_URL" -At -F ' ' -c "SELECT count(*), count(*) FILTER (WHERE state = 'active') FROM pg_stat_activity WHERE datname = current_database();" 2>/dev/null || true)"
    total="0"
    active="0"

    if [[ -n "$conn_line" ]]; then
      read -r maybe_total maybe_active <<< "$conn_line"
      if [[ "$maybe_total" =~ ^[0-9]+$ && "$maybe_active" =~ ^[0-9]+$ ]]; then
        total="$maybe_total"
        active="$maybe_active"
      fi
    fi

    echo "$ts,$app_cpu,$db_cpu,$total,$active" >> "$SAMPLE_CSV"
    sleep 1
  done
}

summarize_samples() {
  awk -F',' 'NR>1 {
    app+=$2; db+=$3;
    if ($2>app_max) app_max=$2;
    if ($3>db_max) db_max=$3;
    if ($4>conn_max) conn_max=$4;
    if ($5>active_max) active_max=$5;
    n++
  }
  END {
    if (n==0) { print "No samples"; exit }
    printf "App CPU avg/max: %.2f / %.2f\n", app/n, app_max;
    printf "DB CPU avg/max: %.2f / %.2f\n", db/n, db_max;
    printf "DB connections total max: %.0f\n", conn_max;
    printf "DB connections active max: %.0f\n", active_max;
  }' "$SAMPLE_CSV"
}

parse_metric_file() {
  local file="$1"
  awk -F',' '$1=="short_links_select_calls"{print $2}' "$file"
}

echo "[1/6] optional pg_stat_statements reset"
reset_pg_stat_statements

echo "[2/6] DB pre snapshot"
collect_db_snapshot > "$PRE_METRICS"
cat "$PRE_METRICS"

echo "[3/6] start runtime sampler"
sampler &
SAMPLER_PID=$!

trap 'kill $SAMPLER_PID >/dev/null 2>&1 || true' EXIT

echo "[4/6] run k6: $SCRIPT_PATH"
k6 run "$SCRIPT_PATH" "$@" | tee "$K6_LOG"

echo "[5/6] stop runtime sampler"
kill "$SAMPLER_PID" >/dev/null 2>&1 || true
wait "$SAMPLER_PID" 2>/dev/null || true
trap - EXIT

echo "[6/6] DB post snapshot"
collect_db_snapshot > "$POST_METRICS"
cat "$POST_METRICS"

pre_select="$(parse_metric_file "$PRE_METRICS")"
post_select="$(parse_metric_file "$POST_METRICS")"

if [[ -n "$pre_select" && -n "$post_select" ]]; then
  echo "DB select calls delta: $((post_select - pre_select))"
else
  echo "DB select calls delta: n/a"
fi

echo "Runtime summary (from external sampler)"
summarize_samples

echo "Artifacts:"
echo "- $PRE_METRICS"
echo "- $POST_METRICS"
echo "- $SAMPLE_CSV"
echo "- $K6_LOG"