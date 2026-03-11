import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SHORT_CODE = __ENV.SHORT_CODE || 'abc123';
const TARGET_RPS = Number(__ENV.TARGET_RPS || 2200);
const TEST_DURATION = __ENV.TEST_DURATION || '60s';
const PRE_ALLOCATED_VUS = Number(__ENV.PRE_ALLOCATED_VUS || 200);
const MAX_VUS = Number(__ENV.MAX_VUS || 1200);
const EXPECTED_LOCATION = __ENV.EXPECTED_LOCATION || 'https://op.gg/ko/lol/champions/annie/build/support';

export const options = {
  scenarios: {
    redirect_traffic: {
      executor: 'constant-arrival-rate',
      rate: TARGET_RPS,
      timeUnit: '1s',
      duration: TEST_DURATION,
      preAllocatedVUs: PRE_ALLOCATED_VUS,
      maxVUs: MAX_VUS,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<300', 'p(99)<800'],
    checks: ['rate>0.99'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  const response = http.get(`${BASE_URL}/s/${SHORT_CODE}`, {
    redirects: 0,
    tags: { endpoint: 'redirect' },
  });

  check(response, {
    'status is 302': (r) => r.status === 302,
    'has location header': (r) => !!r.headers.Location,
    'location is correct': (r) => r.headers.Location === EXPECTED_LOCATION,
  });
}