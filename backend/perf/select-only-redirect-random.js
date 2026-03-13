import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const AUTO_SHORT_CODE_PREFIX = __ENV.AUTO_SHORT_CODE_PREFIX || 'code';
const AUTO_SHORT_CODE_COUNT = Number(__ENV.AUTO_SHORT_CODE_COUNT || 100);
const MIN_SHORT_CODE_COUNT = Number(__ENV.MIN_SHORT_CODE_COUNT || 100);
const ALLOW_SMALL_SET = (__ENV.ALLOW_SMALL_SET || 'false').toLowerCase() === 'true';

const PHASE_1_RATE = Number(__ENV.PHASE_1_RATE || 600);
const PHASE_2_RATE = Number(__ENV.PHASE_2_RATE || 1200);
const PHASE_3_RATE = Number(__ENV.PHASE_3_RATE || 1800);

const PHASE_1_DURATION = __ENV.PHASE_1_DURATION || '60s';
const PHASE_2_DURATION = __ENV.PHASE_2_DURATION || '60s';
const PHASE_3_DURATION = __ENV.PHASE_3_DURATION || '60s';

const PRE_ALLOCATED_VUS = Number(__ENV.PRE_ALLOCATED_VUS || 400);
const MAX_VUS = Number(__ENV.MAX_VUS || 2500);

const VALIDATE_LOCATION = (__ENV.VALIDATE_LOCATION || 'false').toLowerCase() === 'true';
const EXPECTED_LOCATION_BY_CODE = parseExpectedLocationMap(__ENV.EXPECTED_LOCATION_BY_CODE || '');
const EXPECTED_LOCATION = __ENV.EXPECTED_LOCATION || '';

const SHORT_CODES = resolveShortCodes();
validateShortCodeCount(SHORT_CODES);

export const options = {
  scenarios: {
    phase_1_read: createScenario('0s', PHASE_1_RATE, PHASE_1_DURATION, 'phase_1'),
    phase_2_read: createScenario(PHASE_1_DURATION, PHASE_2_RATE, PHASE_2_DURATION, 'phase_2'),
    phase_3_read: createScenario(
      `${parseDurationSeconds(PHASE_1_DURATION) + parseDurationSeconds(PHASE_2_DURATION)}s`,
      PHASE_3_RATE,
      PHASE_3_DURATION,
      'phase_3',
    ),
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
    'http_req_duration{phase:phase_1}': ['p(95)<300', 'p(99)<800'],
    'http_req_duration{phase:phase_2}': ['p(95)<500', 'p(99)<1200'],
    'http_req_duration{phase:phase_3}': ['p(95)<800', 'p(99)<1500'],
    'dropped_iterations{scenario:phase_1_read}': ['count<1'],
    'dropped_iterations{scenario:phase_2_read}': ['count<1'],
    'dropped_iterations{scenario:phase_3_read}': ['count<1'],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

function createScenario(startTime, rate, duration, phase) {
  return {
    executor: 'constant-arrival-rate',
    startTime,
    rate,
    timeUnit: '1s',
    duration,
    preAllocatedVUs: PRE_ALLOCATED_VUS,
    maxVUs: MAX_VUS,
    tags: { phase, traffic: 'redirect-select-only-random-distributed' },
  };
}

function resolveShortCodes() {
  const raw = (__ENV.SHORT_CODES || '')
    .split(',')
    .map((code) => code.trim())
    .filter(Boolean);

  if (raw.length > 0) {
    return raw;
  }

  if (AUTO_SHORT_CODE_COUNT < 1) {
    throw new Error('AUTO_SHORT_CODE_COUNT must be greater than 0.');
  }

  return Array.from({ length: AUTO_SHORT_CODE_COUNT }, (_, index) => `${AUTO_SHORT_CODE_PREFIX}${String(index + 1).padStart(3, '0')}`);
}

function validateShortCodeCount(shortCodes) {
  if (!ALLOW_SMALL_SET && shortCodes.length < MIN_SHORT_CODE_COUNT) {
    throw new Error(
      `At least ${MIN_SHORT_CODE_COUNT} short codes are required. Current count=${shortCodes.length}. ` +
        'Use MIN_SHORT_CODE_COUNT / ALLOW_SMALL_SET if you intentionally want a smaller set.',
    );
  }
}

function parseDurationSeconds(rawDuration) {
  const match = rawDuration.match(/^(\d+)(s|m)$/);
  if (!match) {
    throw new Error(`Unsupported duration format: ${rawDuration}. Use Ns or Nm.`);
  }

  const value = Number(match[1]);
  return match[2] === 'm' ? value * 60 : value;
}

function parseExpectedLocationMap(raw) {
  if (!raw) {
    return {};
  }

  return raw.split(',').reduce((acc, pair) => {
    const [code, ...urlParts] = pair.split('=');
    const shortCode = (code || '').trim();
    const url = urlParts.join('=').trim();

    if (shortCode && url) {
      acc[shortCode] = url;
    }

    return acc;
  }, {});
}

function randomShortCode() {
  const index = Math.floor(Math.random() * SHORT_CODES.length);
  return SHORT_CODES[index];
}

function expectedLocationFor(shortCode) {
  if (EXPECTED_LOCATION_BY_CODE[shortCode]) {
    return EXPECTED_LOCATION_BY_CODE[shortCode];
  }

  return EXPECTED_LOCATION;
}

export default function () {
  const shortCode = randomShortCode();
  const expectedLocation = expectedLocationFor(shortCode);

  const response = http.get(`${BASE_URL}/s-select/${shortCode}`, {
    redirects: 0,
    tags: { endpoint: 'redirect-select-only', short_code: shortCode },
  });

  const assertions = {
    'status is 302': (r) => r.status === 302,
    'has location header': (r) => !!r.headers.Location,
  };

  if (VALIDATE_LOCATION) {
    assertions['location is expected'] = (r) => {
      if (!expectedLocation) {
        return false;
      }
      return r.headers.Location === expectedLocation;
    };
  }

  check(response, assertions);
}

export function handleSummary(data) {
  let output = '\n=== Summary ===\n';
  output += `checks rate: ${data.metrics.checks?.values?.rate ?? 'n/a'}\n`;
  output += `http_req_failed rate: ${data.metrics.http_req_failed?.values?.rate ?? 'n/a'}\n`;
  output += `http_req_duration p95: ${data.metrics.http_req_duration?.values?.['p(95)'] ?? 'n/a'}\n`;
  output += `http_req_duration p99: ${data.metrics.http_req_duration?.values?.['p(99)'] ?? 'n/a'}\n`;

  return { stdout: output };
}