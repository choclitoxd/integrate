// RNF-08 (Plan §4.2.2) y métrica "Tiempo de respuesta en autenticación" (§3.4.1):
// POST /auth/login (Argon2id + emisión del JWT RS256) con p95 < 800 ms bajo carga nominal.
// Uso: k6 run -e USER_PASSWORD=... qa/k6/login.js
import { check } from 'k6';
import { post, registerTourist } from './lib/fixtures.js';

const USERS = 20;

export const options = {
  setupTimeout: '120s',
  scenarios: {
    nominal: {
      executor: 'constant-arrival-rate',
      rate: 4,
      timeUnit: '1s',
      duration: '60s',
      preAllocatedVUs: 10,
      maxVUs: 40,
    },
  },
  thresholds: {
    'http_req_duration{name:login}': ['p(95)<800'],
    'checks{check:login_ok}': ['rate==1'],
  },
};

export function setup() {
  const password = __ENV.USER_PASSWORD;
  const emails = [];
  for (let i = 0; i < USERS; i++) emails.push(registerTourist(password));
  return { emails, password };
}

export default function (data) {
  const email = data.emails[(__VU + __ITER) % data.emails.length];
  const response = post('/auth/login', { email, password: data.password }, null, {}, { name: 'login' });
  check(response, {
    login_ok: (r) => r.status === 200 && typeof r.json('accessToken') === 'string',
  });
}
