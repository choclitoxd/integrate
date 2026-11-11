// Humo funcional en el entorno desplegado (ADR-0055, con ADR-0053, ADR-0054, ADR-0058 y ADR-0060).
// Una sola iteración: comprueba lo que depende del entorno real (migraciones sobre Neon, variables,
// cabeceras, salud) antes de la carga. Crea datos sintéticos por la API; solo contra `pruebas`.
// Uso: k6 run -e BASE_URL=... -e ADMIN_EMAIL=... -e ADMIN_PASSWORD=... -e USER_PASSWORD=... smoke-desplegado.js
import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, catalogScenario, login, post, uuid } from './lib/fixtures.js';

const API = `${BASE_URL}/api/v1`;

export const options = {
  vus: 1,
  iterations: 1,
  setupTimeout: '180s',
  thresholds: { checks: ['rate==1'] },
};

export function setup() {
  return catalogScenario(1, __ENV.USER_PASSWORD);
}

function lockBody(scheduleId, doc, eps) {
  const holder = { fullName: 'Turista de Humo', docType: 'PA', docNumber: doc, isHolder: true };
  if (eps) holder.medicalEps = eps;
  return {
    scheduleId,
    requestedSlots: 1,
    emergencyContact: { fullName: 'Contacto Humo', phone: '+573001234567' },
    participants: [holder],
  };
}

export default function (data) {
  const password = __ENV.USER_PASSWORD;

  // ADR-0053: salud pública y acotada.
  const health = http.get(`${API}/health`);
  check(health, {
    'salud 200': (r) => r.status === 200,
    'salud sin detalles': (r) => !/(db|diskSpace|components|details)/i.test(String(r.body)),
  });

  // ADR-0054: la política publicada expone el aviso solo cuando existe uno aprobado.
  const policy = http.get(`${API}/privacy-policies/current`);
  check(policy, { 'política vigente 200': (r) => r.status === 200 });
  const notice = policy.status === 200 ? policy.json().healthNotice : undefined;
  check(policy, { 'aviso de salud no aprobado (esperado en pruebas)': () => notice === null || notice === undefined });

  // ADR-0060: el rol HOST es obligatorio y no hay acceso anónimo.
  check(http.get(`${API}/host/bookings`), { 'listado de anfitrión anónimo 401': (r) => r.status === 401 });
  check(http.get(`${API}/host/bookings/${uuid()}/holder-eps`), { 'holder-eps anónimo 401': (r) => r.status === 401 });

  // Turista con documento conocido, solo consentimiento de datos personales.
  const email = `k6-humo-${uuid().slice(0, 10)}@carga.integrate.test`;
  const doc = `H${uuid().replace(/-/g, '').slice(0, 12)}`;
  const registered = post('/auth/register', {
    email,
    password,
    fullName: 'Turista de Humo',
    docType: 'PA',
    docNumber: doc,
    phone: '+573001234567',
    consent: { policyVersion: policy.json().version, purposes: ['PERSONAL_DATA', 'HEALTH_EMERGENCY'] },
  });
  check(registered, { 'registro de turista 201': (r) => r.status === 201 });
  const tourist = login(email, password);

  check(http.get(`${API}/host/bookings`, { headers: { Authorization: `Bearer ${tourist}` } }), {
    'turista no ve reservas de anfitrión 403': (r) => r.status === 403,
  });

  // ADR-0054: se puede reservar sin EPS; pedir EPS sin aviso aprobado está bloqueado.
  const noEps = post('/bookings/lock', lockBody(data.scheduleIds[0], doc), tourist, { 'Idempotency-Key': uuid() });
  check(noEps, { 'reserva sin EPS 201': (r) => r.status === 201 });
  const withEps = post('/bookings/lock', lockBody(data.scheduleIds[0], doc, 'Sura'), tourist, {
    'Idempotency-Key': uuid(),
  });
  check(withEps, {
    'EPS sin aviso aprobado 403': (r) => r.status === 403,
    'EPS bloqueada identificada': (r) => /health-eps-unavailable|HEALTH_EPS_UNAVAILABLE/.test(String(r.body)),
  });

  // ADR-0060: un turista sin perfil de anfitrión no consulta EPS ajenas.
  check(http.get(`${API}/host/bookings/${uuid()}/holder-eps`, { headers: { Authorization: `Bearer ${tourist}` } }), {
    'holder-eps de turista 403': (r) => r.status === 403,
  });
}
