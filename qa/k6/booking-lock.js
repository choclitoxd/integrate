// RNF-02 (Plan de Gestión de la Calidad §4.2.1): bloqueo pesimista de aforo con tráfico HTTP real.
//   - p95 de POST /bookings/lock <= 200 ms
//   - >= 100 transacciones por minuto sin degradación (se ejercita el triple: 5 tx/s)
//   - sin deadlocks ni 5xx (solo 201, 409 sin cupos o 503 lock_timeout con Retry-After)
// Uso: k6 run -e ADMIN_EMAIL=... -e ADMIN_PASSWORD=... -e USER_PASSWORD=... qa/k6/booking-lock.js
import { check } from 'k6';
import { catalogScenario, lock, login, registerTourist } from './lib/fixtures.js';

const TOURISTS = 20;

export const options = {
  setupTimeout: '180s',
  scenarios: {
    sostenido: {
      executor: 'constant-arrival-rate',
      rate: 5,
      timeUnit: '1s',
      duration: '60s',
      preAllocatedVUs: 20,
      maxVUs: 50,
      tags: { escenario: 'sostenido' },
    },
    rafaga: {
      executor: 'per-vu-iterations',
      vus: 100,
      iterations: 1,
      startTime: '65s',
      maxDuration: '60s',
      tags: { escenario: 'rafaga' },
    },
  },
  thresholds: {
    'http_req_duration{name:lock,escenario:sostenido}': ['p(95)<=200'],
    'http_req_duration{name:lock,escenario:rafaga}': ['p(95)<=1000'],
    'checks{check:sin_5xx_inesperado}': ['rate==1'],
    'iterations{escenario:sostenido}': ['count>=100'],
  },
};

export function setup() {
  const password = __ENV.USER_PASSWORD;
  const catalog = catalogScenario(15, password); // 15 franjas x 30 = 450 cupos para 400 bloqueos
  const tokens = [];
  for (let i = 0; i < TOURISTS; i++) tokens.push(login(registerTourist(password), password));
  return { tokens, scheduleIds: catalog.scheduleIds };
}

export default function (data) {
  const token = data.tokens[(__VU + __ITER) % data.tokens.length];
  const scheduleId = data.scheduleIds[Math.floor(Math.random() * data.scheduleIds.length)];
  const response = lock(token, scheduleId);
  check(response, {
    sin_5xx_inesperado: (r) => r.status === 201 || r.status === 409 || r.status === 503,
    reserva_creada: (r) => r.status === 201,
  });
}
