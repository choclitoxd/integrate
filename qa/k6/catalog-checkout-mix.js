// RNF-10 (Plan §4.2.4): capacidad en temporada alta — 500 usuarios navegando el catálogo y 100 procesos
// de checkout simultáneos. El plan fija además CPU del servidor < 75 %: se mide aparte durante la
// corrida (docker stats / monitor del sistema) y se anota en el informe de evidencias.
// Uso: k6 run -e ADMIN_EMAIL=... -e ADMIN_PASSWORD=... -e USER_PASSWORD=... qa/k6/catalog-checkout-mix.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, catalogScenario, lock, login, registerTourist } from './lib/fixtures.js';

const TOURISTS = 20;

export const options = {
  setupTimeout: '180s',
  scenarios: {
    navegacion: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '20s', target: 500 },
        { duration: '40s', target: 500 },
        { duration: '10s', target: 0 },
      ],
      exec: 'browse',
      tags: { escenario: 'navegacion' },
    },
    checkout: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '20s', target: 100 },
        { duration: '40s', target: 100 },
        { duration: '10s', target: 0 },
      ],
      exec: 'checkout',
      tags: { escenario: 'checkout' },
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{escenario:navegacion}': ['p(95)<1500'],
    'http_req_duration{name:lock}': ['p(95)<1500'],
    'checks{check:sin_5xx_inesperado}': ['rate==1'],
  },
};

export function setup() {
  const password = __ENV.USER_PASSWORD;
  const catalog = catalogScenario(60, password); // 60 franjas x 30 = 1.800 cupos
  const tokens = [];
  for (let i = 0; i < TOURISTS; i++) tokens.push(login(registerTourist(password), password));
  return { ...catalog, tokens };
}

export function browse(data) {
  const api = `${BASE_URL}/api/v1`;
  const to = new Date(Date.parse(data.dateFrom) + 29 * 86_400_000).toISOString().slice(0, 10);
  const responses = http.batch([
    ['GET', `${api}/experiences?page=0&size=10`, null, { tags: { name: 'catalogo' } }],
    ['GET', `${api}/experiences/${data.experienceId}`, null, { tags: { name: 'detalle' } }],
    [
      'GET',
      `${api}/experiences/${data.experienceId}/availability?dateFrom=${data.dateFrom}&dateTo=${to}`,
      null,
      { tags: { name: 'disponibilidad' } },
    ],
  ]);
  check(responses, { navegacion_ok: (rs) => rs.every((r) => r.status === 200) });
  sleep(1 + Math.random() * 2); // tiempo de lectura de un turista
}

export function checkout(data) {
  const token = data.tokens[(__VU + __ITER) % data.tokens.length];
  const scheduleId = data.scheduleIds[Math.floor(Math.random() * data.scheduleIds.length)];
  const response = lock(token, scheduleId);
  check(response, {
    sin_5xx_inesperado: (r) => r.status === 201 || r.status === 409 || r.status === 503,
  });
  sleep(2 + Math.random() * 3); // diligenciar el formulario entre reservas
}
