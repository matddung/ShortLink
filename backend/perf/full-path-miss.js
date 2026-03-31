import http from 'k6/http';
import { check } from 'k6';

http.setResponseCallback(http.expectedStatuses(404));

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TARGET_RPS = Number(__ENV.TARGET_RPS || 1000);
const TEST_DURATION = __ENV.TEST_DURATION || '60s';
const PRE_ALLOCATED_VUS = Number(__ENV.PRE_ALLOCATED_VUS || 200);
const MAX_VUS = Number(__ENV.MAX_VUS || 1200);

const DISABLE_THRESHOLDS = (__ENV.DISABLE_THRESHOLDS || 'false').toLowerCase() === 'true';

export const options = {
  scenarios: {
    full_path_miss_traffic: {
      executor: 'constant-arrival-rate',
      rate: TARGET_RPS,
      timeUnit: '1s',
      duration: TEST_DURATION,
      preAllocatedVUs: PRE_ALLOCATED_VUS,
      maxVUs: MAX_VUS,
    },
  },
  thresholds: DISABLE_THRESHOLDS
    ? {}
    : {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<300', 'p(99)<800'],
        checks: ['rate>0.99'],
      },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

function randomMissingCode() {
  return `missing-${Math.random().toString(36).slice(2, 12)}`;
}

export default function () {
  const code = randomMissingCode();

  const response = http.get(`${BASE_URL}/api/s/${code}`, {
    redirects: 0,
    tags: {
      endpoint: 'redirect-miss-full-path',
      scenario: 'miss',
      mode: 'full-path',
    },
  });

  check(response, {
    'status is 404': (r) => r.status === 404,
  });
}

export function handleSummary(data) {
  const checksRate = data.metrics.checks?.values?.rate ?? 'n/a';
  const failedRate =
    data.metrics.http_req_failed?.values?.value ??
    data.metrics.http_req_failed?.values?.rate ??
    'n/a';
  const p95 = data.metrics.http_req_duration?.values?.['p(95)'] ?? 'n/a';
  const p99 = data.metrics.http_req_duration?.values?.['p(99)'] ?? 'n/a';
  const reqRate = data.metrics.http_reqs?.values?.rate ?? 'n/a';
  const reqCount = data.metrics.http_reqs?.values?.count ?? 'n/a';
  const dropped = data.metrics.dropped_iterations?.values?.count ?? 0;
  const vusMax = data.metrics.vus_max?.values?.value ?? 'n/a';

  let output = '\n=== Summary ===\n';
  output += `checks rate: ${checksRate}\n`;
  output += `http_req_failed value: ${failedRate}\n`;
  output += `http_reqs rate: ${reqRate}\n`;
  output += `http_reqs count: ${reqCount}\n`;
  output += `dropped_iterations count: ${dropped}\n`;
  output += `vus_max value: ${vusMax}\n`;
  output += `http_req_duration p95: ${p95}\n`;
  output += `http_req_duration p99: ${p99}\n`;

  return { stdout: output };
}