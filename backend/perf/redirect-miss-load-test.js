import http from 'k6/http';
import { check } from 'k6';

http.setResponseCallback(http.expectedStatuses(404));

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const MISSING_SHORT_CODE = __ENV.MISSING_SHORT_CODE || 'not-exists-001';
const TARGET_RPS = Number(__ENV.TARGET_RPS || 1200);
const TEST_DURATION = __ENV.TEST_DURATION || '60s';
const PRE_ALLOCATED_VUS = Number(__ENV.PRE_ALLOCATED_VUS || 200);
const MAX_VUS = Number(__ENV.MAX_VUS || 1200);

export const options = {
  scenarios: {
    redirect_miss_traffic: {
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
    checks: ['rate>0.99'],
    http_req_duration: ['p(95)<300', 'p(99)<800'],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  const response = http.get(`${BASE_URL}/s/${MISSING_SHORT_CODE}`, {
    redirects: 0,
    tags: { endpoint: 'redirect-miss', traffic: 'missing-shortcode' },
  });

  check(response, {
    'status is 404': (r) => r.status === 404,
    'body has LINK_NOT_FOUND code': (r) => {
      try {
        const body = JSON.parse(r.body || '{}');
        return body.code === 'LINK_NOT_FOUND';
      } catch (_) {
        return false;
      }
    },
  });
}