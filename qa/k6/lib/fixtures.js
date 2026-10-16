// Fixtures compartidos de las pruebas de carga (ADR-0019 §5). Todo se crea por la API pública del
// sistema contra un entorno desechable: nunca contra producción ni contra la base de desarrollo.
// Credenciales: solo por variables de entorno de k6 (-e), nunca en este archivo (SEC-DIR-004).
import http from 'k6/http';
import { check, fail } from 'k6';

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const API = `${BASE_URL}/api/v1`;
const JSON_HEADERS = { 'Content-Type': 'application/json' };

/** Identificador aleatorio (Idempotency-Key, correos únicos) sin depender de jslib remotos. */
export function uuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
  });
}

function must(response, expected, what) {
  if (!check(response, { [`${what}: ${expected}`]: (r) => r.status === expected })) {
    fail(`${what} respondió ${response.status}: ${response.body}`);
  }
  return response.status === 204 ? null : response.json();
}

export function post(path, body, token, extraHeaders = {}, tags = {}) {
  const headers = { ...JSON_HEADERS, ...extraHeaders };
  if (token) headers.Authorization = `Bearer ${token}`;
  return http.post(`${API}${path}`, body === undefined ? null : JSON.stringify(body), { headers, tags });
}

export function login(email, password) {
  return must(post('/auth/login', { email, password }), 200, 'login').accessToken;
}

/** Turista con consentimiento de datos personales y de salud (precondición de la reserva). */
export function registerTourist(password) {
  const email = `k6-${uuid().slice(0, 12)}@carga.integrate.test`;
  const doc = `K${uuid().replace(/-/g, '').slice(0, 12)}`;
  must(
    post('/auth/register', {
      email,
      password,
      fullName: 'Turista de Carga',
      docType: 'PA',
      docNumber: doc,
      phone: '+573001234567',
      consent: { policyVersion: 'v0.1', purposes: ['PERSONAL_DATA', 'HEALTH_EMERGENCY'] },
    }),
    201,
    'registro de turista',
  );
  return email;
}

/**
 * Asociación + anfitrión avalado + experiencia aprobada (capacidad 30) + `schedules` franjas de 30 cupos.
 * Requiere el administrador inicial del entorno (BOOTSTRAP_ADMIN_EMAIL/PASSWORD).
 */
export function catalogScenario(schedules, password) {
  const adminEmail = __ENV.ADMIN_EMAIL;
  const adminPassword = __ENV.ADMIN_PASSWORD;
  if (!adminEmail || !adminPassword) fail('Defina -e ADMIN_EMAIL=... -e ADMIN_PASSWORD=...');
  const admin = login(adminEmail, adminPassword);
  const seq = Math.floor(Math.random() * 1e8);
  const association = must(
    post(
      '/admin/associations',
      {
        nit: `9${String(seq).padStart(8, '0')}-1`,
        rntNumber: String(1_000_000 + (seq % 1_000_000)),
        legalName: 'Asociación de Carga k6',
        associationType: 'ASOCIACION_VICTIMAS',
        municipalityCode: '05001',
        address: 'Vereda La Esperanza',
        contactEmail: 'carga@integrate.test',
        contactPhone: '+573001234567',
      },
      admin,
    ),
    201,
    'asociación',
  );
  const hostEmail = registerTourist(password);
  const hostId = must(
    post('/auth/login', { email: hostEmail, password }),
    200,
    'login anfitrión',
  ).user.id;
  must(
    post('/admin/host-profiles', { userId: hostId, associationId: association.id }, admin),
    201,
    'perfil de anfitrión',
  );
  const host = login(hostEmail, password);
  const experience = must(
    post(
      '/host/experiences',
      {
        title: `Sendero de carga ${seq}`,
        description: 'Recorrido guiado por la comunidad.',
        safetyGuidelines: 'Llevar agua.',
        physicalIntensity: 'MEDIUM',
        durationMinutes: 240,
        municipalityCode: '05001',
        vereda: 'La Esperanza',
        meetingPoint: 'Parque principal',
        latitude: 6.25,
        longitude: -75.56,
        maxCapacityPerSlot: 30,
        unitPriceCop: 80000,
        categoryCodes: ['SENDERISMO_MEMORIA'],
      },
      host,
    ),
    201,
    'experiencia',
  );
  must(post(`/host/experiences/${experience.id}/submit`, undefined, host), 200, 'envío a revisión');
  must(post(`/admin/experiences/${experience.id}/approve`, undefined, admin), 200, 'aprobación');

  const base = new Date();
  const days = [];
  for (let i = 0; i < schedules; i++) {
    const d = new Date(base.getTime() + (20 + i + (seq % 200)) * 86_400_000).toISOString().slice(0, 10);
    days.push({ localStart: `${d}T07:00:00`, localEnd: `${d}T12:00:00`, totalSlots: 30 });
  }
  const created = must(
    post(`/host/experiences/${experience.id}/schedules`, { schedules: days }, host),
    201,
    'franjas',
  );
  return { experienceId: experience.id, scheduleIds: created.map((s) => s.id), dateFrom: days[0].localStart.slice(0, 10) };
}

export function lockBody(scheduleId) {
  return {
    scheduleId,
    requestedSlots: 1,
    emergencyContact: { fullName: 'Carlos Gomez', phone: '+573001234567' },
    participants: [
      { fullName: 'Participante Uno', docType: 'CC', docNumber: '50940000', medicalEps: 'Sura', isHolder: true },
    ],
  };
}

export function lock(token, scheduleId) {
  return post('/bookings/lock', lockBody(scheduleId), token, { 'Idempotency-Key': uuid() }, { name: 'lock' });
}
