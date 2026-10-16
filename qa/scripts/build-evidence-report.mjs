// ADR-0019 §7: consolida la evidencia de la suite de calidad en la sección 5 del Plan de Gestión de la Calidad.
// Lee lo que dejan las ejecuciones (no recalcula nada) y genera:
//   docs/quality/evidencias-metricas.md   (sección 5 · Evidencias métricas)
//   docs/quality/matriz-trazabilidad-rnf.md
//   docs/quality/evidencias-metricas.json (insumo para el documento Word)
// Uso (desde la raíz): node qa/scripts/build-evidence-report.mjs
import { existsSync, readdirSync, readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { join, resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');
const BACKEND = join(ROOT, 'backend-api', 'target');
const FRONTEND = join(ROOT, 'frontend-pwa', 'coverage');
const K6 = join(ROOT, 'qa', 'k6', 'results');
const OUT = join(ROOT, 'docs', 'quality');

const readJson = (path) => JSON.parse(readFileSync(path, 'utf8').replace(/^﻿/, ''));
const fmt = (n, digits = 1) =>
  Number(n).toLocaleString('es-CO', { minimumFractionDigits: 0, maximumFractionDigits: digits });

// --------------------------------------------------------------- catálogo de RNF del plan (campo ID)
const ISO = {
  S: 'Seguridad',
  E: 'Eficiencia de desempeño',
  I: 'Capacidad de interacción',
  F: 'Fiabilidad',
  A: 'Adecuación funcional',
  M: 'Mantenibilidad',
};
const P1 = 'P1 Identidades';
const P2 = 'P2 Reserva';
const P3 = 'P3 Validación y split';
const RNF = [
  ['RNF-01', 'S', P1, 'Cifrado en reposo/tránsito y anonimización', 'Parcial', 'SensitiveDataAtRestQualityTest', 'TLS 1.3 A+ se audita en el despliegue (T-2.16)'],
  ['RNF-04', 'S', P1, 'Hashing y custodia de credenciales', 'Automatizada', 'CredentialStorageQualityTest, SecretRedactionQualityTest', 'El plan nombra BCrypt; el sistema usa Argon2id (ADR-0005 §3)'],
  ['RNF-05', 'S', P3, 'Firma criptográfica de vouchers', 'Parcial', 'VoucherTamperDetectionQualityTest', 'Verificación en la PWA sin conexión: T-2.10'],
  ['RNF-06', 'S', P1, 'Bitácora inmutable de Habeas Data', 'Automatizada', 'ConsentAuditQualityTest', ''],
  ['RNF-07', 'S', P1, 'RBAC y seguridad en endpoints', 'Automatizada', 'EndpointSecuritySweepQualityTest', ''],
  ['RNF-02', 'E', P2, 'Bloqueo transaccional < 200 ms', 'Automatizada', 'BookingLockLatencyQualityTest, qa/k6/booking-lock.js', ''],
  ['RNF-08', 'E', P1, 'Latencia de emisión de JWT < 800 ms', 'Automatizada', 'LoginLatencyQualityTest, qa/k6/login.js', ''],
  ['RNF-09', 'E', P3, 'Peso del bundle PWA < 2,5 MB', 'Automatizada', 'frontend-pwa/scripts/check-bundle-budget.mjs', ''],
  ['RNF-10', 'E', P2, '500 navegando + 100 checkout, CPU < 75 %', 'Automatizada', 'qa/k6/catalog-checkout-mix.js', 'CPU medida en la máquina local, no en AWS'],
  ['RNF-11', 'E', P2, 'Worker de expiración TTL < 1,5 s', 'Automatizada', 'ExpirationSweepQualityTest', ''],
  ['RNF-03', 'I', P1, 'Accesibilidad WCAG 2.1 AA / NTC 5854', 'Parcial', 'accessibility.quality.test.tsx, styles/tokens.test.ts', 'Áreas táctiles y lector de pantalla: auditoría manual T-2.15'],
  ['RNF-12', 'I', P1, 'Enrolamiento asistido < 3 min', 'No automatizable', '—', 'Prueba de usuario en campo con el Gestor de Enlace'],
  ['RNF-13', 'I', P2, 'Protección contra errores en checkout', 'Automatizada', 'checkoutGuards.quality.test.tsx', ''],
  ['RNF-14', 'I', P3, 'Indicadores visuales de validación offline', 'Pendiente Fase 2', '—', 'T-2.10 (lector QR)'],
  ['RNF-15', 'I', P1, 'Legibilidad del consentimiento (IFSZ > 60)', 'Automatizada', 'consentReadability.quality.test.ts', ''],
  ['RNF-16', 'F', P2, 'Tasa cero de overbooking', 'Automatizada', 'OverbookingStressQualityTest', ''],
  ['RNF-17', 'F', P3, 'Operación offline (IndexedDB)', 'Pendiente Fase 2', '—', 'T-2.8, T-2.9'],
  ['RNF-18', 'F', P3, 'Prevención de doble canje offline', 'Pendiente Fase 2', '—', 'T-2.11'],
  ['RNF-19', 'F', P3, 'Background Sync determinista', 'Pendiente Fase 2', '—', 'T-2.3, T-2.13'],
  ['RNF-20', 'F', P3, 'Contingencia por falla del terminal', 'Pendiente Fase 2', '—', 'T-2.2, T-2.12'],
  ['RNF-21', 'A', P3, 'Exactitud de la dispersión 85/15', 'Parcial', 'SplitPaymentExactnessQualityTest', 'Se verifica la custodia; la orden de giro es T-2.5'],
  ['RNF-22', 'A', P3, 'Política de cancelación 72 h', 'Pendiente Fase 2', '—', 'T-2.6'],
  ['RNF-23', 'A', P2, 'Voucher PDF/A completo', 'Automatizada', 'VoucherPdfConformanceQualityTest', ''],
  ['RNF-24', 'A', P2, 'Restricción de la capacidad de carga', 'Automatizada', 'CapacityParametrizationQualityTest', ''],
  ['RNF-25', 'A', P2, 'Idempotencia de webhooks', 'Automatizada', 'WebhookIdempotencyQualityTest', ''],
  ['RNF-26', 'M', P2, 'Desacoplamiento de workers', 'Automatizada', 'WorkerDecouplingQualityTest', ''],
  ['RNF-27', 'M', P1, 'Cobertura > 80 % en lógica crítica', 'Automatizada', 'JaCoCo (check en mvn verify), Vitest coverage', ''],
  ['RNF-28', 'M', P2, 'Estándares de código y OpenAPI', 'Automatizada', 'OpenApiCoverageQualityTest, npm run lint', 'SonarQube no se usa: JaCoCo + ESLint (ADR-0019)'],
  ['RNF-29', 'M', P3, 'Logs JSON con correlation ID (MTTD < 30 min)', 'Brecha', '—', 'Hay traceId (TraceIdFilter) pero no salida JSON estructurada'],
  ['RNF-30', 'M', P1, 'Reusabilidad de componentes UI', 'Automatizada', 'componentReuse.quality.test.ts', ''],
].map(([id, iso, process, name, state, evidence, note]) => ({ id, iso: ISO[iso], process, name, state, evidence, note }));

// ------------------------------------------------------------------------- lectura de evidencias
// Los nombres de métrica del backend van sin tildes (se usan para el nombre del archivo); aquí se restituyen.
const ACCENTS = {
  Contrasenas: 'Contraseñas', anfitrion: 'anfitrión', victima: 'víctima', publicos: 'públicos', publico: 'público',
  telefono: 'teléfono', deteccion: 'detección', bitacora: 'bitácora', autenticacion: 'autenticación',
  Rafaga: 'Ráfaga', simultaneos: 'simultáneos', maximo: 'máximo', ejecucion: 'ejecución', liberacion: 'liberación',
  catalogo: 'catálogo', dispersion: 'dispersión', lineas: 'líneas', criptografia: 'criptografía', Envios: 'Envíos',
  mas: 'más', modulos: 'módulos', autorizacion: 'autorización', valido: 'válido', Metrica: 'Métrica',
};
const accent = (text) => text.replace(/\p{L}+/gu, (w) => ACCENTS[w] ?? w);
const metrics = [];
const add = (m, source) => metrics.push({ ...m, metric: accent(m.metric), source });

const backendMetrics = join(BACKEND, 'quality-metrics');
if (existsSync(backendMetrics)) {
  for (const file of readdirSync(backendMetrics).filter((f) => f.endsWith('.json'))) {
    add(readJson(join(backendMetrics, file)), 'Backend (JUnit + Testcontainers)');
  }
}

const vitest = join(FRONTEND, 'vitest-results.json');
if (existsSync(vitest)) {
  for (const file of readJson(vitest).testResults) {
    for (const test of file.assertionResults) {
      for (const m of test.meta?.quality ?? []) add(m, 'PWA (Vitest)');
    }
  }
}
const bundle = join(FRONTEND, 'bundle-budget.json');
if (existsSync(bundle)) add(readJson(bundle), 'PWA (build de producción)');

// Cobertura RNF-27: backend por paquetes del umbral, PWA del resumen de Vitest.
const jacoco = join(BACKEND, 'site', 'jacoco', 'jacoco.csv');
if (existsSync(jacoco)) {
  const rows = readFileSync(jacoco, 'utf8').trim().split(/\r?\n/).slice(1).map((l) => l.split(','));
  const critical = rows.filter(([, pkg]) => /^com\.integrate\.modules\.[a-z]+\.service/.test(pkg) || pkg === 'com.integrate.common.crypto');
  const sum = (rs, i) => rs.reduce((s, r) => s + Number(r[i]), 0);
  const pct = (rs) => (100 * sum(rs, 8)) / (sum(rs, 7) + sum(rs, 8));
  const byPkg = {};
  for (const r of critical) {
    byPkg[r[1]] ??= [];
    byPkg[r[1]].push(r);
  }
  const lowest = Object.entries(byPkg).map(([p, rs]) => [p, pct(rs)]).sort((a, b) => a[1] - b[1])[0];
  add({ rnf: 'RNF-27', metric: 'Cobertura de lineas backend (servicios y criptografia)', value: pct(critical), unit: '%', threshold: '> 80 %', pass: pct(critical) > 80 && lowest[1] >= 80, detail: `${Object.keys(byPkg).length} paquetes; el más bajo ${lowest[0].replace('com.integrate.', '')} con ${fmt(lowest[1])} %; total del backend ${fmt(pct(rows))} %` }, 'Backend (JaCoCo)');
}
const vcov = join(FRONTEND, 'coverage-summary.json');
if (existsSync(vcov)) {
  const t = readJson(vcov).total;
  add({ rnf: 'RNF-27', metric: 'Cobertura de lineas PWA (features y shared)', value: t.lines.pct, unit: '%', threshold: '> 80 %', pass: t.lines.pct > 80, detail: `sentencias ${fmt(t.statements.pct)} %, ramas ${fmt(t.branches.pct)} %, funciones ${fmt(t.functions.pct)} %` }, 'PWA (Vitest + V8)');
}

// k6: valores del summary export (en sus umbrales, `false` significa que no se cruzaron).
const k6 = (name) => (existsSync(join(K6, `${name}.json`)) ? readJson(join(K6, `${name}.json`)).metrics : null);
const login = k6('login');
if (login) {
  const d = login['http_req_duration{name:login}'];
  add({ rnf: 'RNF-08', metric: 'Tiempo de respuesta en autenticacion HTTP (p95, k6)', value: d['p(95)'], unit: 'ms', threshold: 'p95 < 800 ms', pass: d['p(95)'] < 800, detail: `${login.iterations.count} logins a 4/s durante 60 s; media ${fmt(d.avg)} ms, máx. ${fmt(d.max)} ms` }, 'Carga (k6)');
}
const lock = k6('booking-lock');
if (lock) {
  const s = lock['http_req_duration{name:lock,escenario:sostenido}'];
  const b = lock['http_req_duration{name:lock,escenario:rafaga}'];
  const it = lock['iterations{escenario:sostenido}'];
  const ok = lock['checks{check:sin_5xx_inesperado}'];
  add({ rnf: 'RNF-02', metric: 'Tiempo de bloqueo de aforo HTTP sostenido (p95, k6)', value: s['p(95)'], unit: 'ms', threshold: 'p95 <= 200 ms', pass: s['p(95)'] <= 200, detail: `${it.count} bloqueos a 5 tx/s (300 tx/min, el triple del mínimo); media ${fmt(s.avg)} ms` }, 'Carga (k6)');
  add({ rnf: 'RNF-02', metric: 'Rafaga de 100 bloqueos simultaneos (p95, k6)', value: b['p(95)'], unit: 'ms', threshold: 'informativo (pool de 10 conexiones)', pass: true, detail: `100 usuarios virtuales a la vez; media ${fmt(b.avg)} ms, máx. ${fmt(b.max)} ms; sin 5xx: ${fmt(ok.value * 100)} %` }, 'Carga (k6)');
  add({ rnf: 'RNF-02', metric: 'Transacciones de bloqueo por minuto (k6)', value: it.count, unit: 'tx/min', threshold: '>= 100 tx/min', pass: it.count >= 100, detail: 'escenario sostenido de 60 s' }, 'Carga (k6)');
}
const mix = k6('catalog-checkout-mix');
if (mix) {
  const nav = mix['http_req_duration{escenario:navegacion}'];
  const lk = mix['http_req_duration{name:lock}'];
  const ok = mix['checks{check:sin_5xx_inesperado}'];
  add({ rnf: 'RNF-10', metric: '500 usuarios navegando: tiempo de respuesta (p95, k6)', value: nav['p(95)'], unit: 'ms', threshold: 'p95 < 1500 ms, < 1 % de errores', pass: nav['p(95)'] < 1500 && mix.http_req_failed.value < 0.01, detail: `${mix.http_reqs.count} peticiones (${fmt(mix.http_reqs.rate)}/s); errores HTTP ${fmt(mix.http_req_failed.value * 100, 2)} % (409 por cupos agotados)` }, 'Carga (k6)');
  add({ rnf: 'RNF-10', metric: '100 checkouts simultaneos: bloqueo (p95, k6)', value: lk['p(95)'], unit: 'ms', threshold: 'p95 < 1500 ms, sin 5xx', pass: lk['p(95)'] < 1500 && ok.value === 1, detail: `en paralelo con la navegación; sin 5xx inesperados: ${fmt(ok.value * 100)} %` }, 'Carga (k6)');
}
if (existsSync(join(K6, 'cpu-rnf10.json'))) add(readJson(join(K6, 'cpu-rnf10.json')), 'Carga (k6) + monitor de CPU');

// Totales de ejecución (Surefire y Vitest).
const surefire = join(BACKEND, 'surefire-reports');
let backendTests = { tests: 0, failures: 0, skipped: 0 };
if (existsSync(surefire)) {
  for (const f of readdirSync(surefire).filter((x) => /^TEST-.*\.xml$/.test(x))) {
    const head = readFileSync(join(surefire, f), 'utf8').slice(0, 600);
    const n = (k) => Number((head.match(new RegExp(`${k}="(\\d+)"`)) ?? [0, 0])[1]);
    backendTests.tests += n('tests');
    backendTests.failures += n('failures') + n('errors');
    backendTests.skipped += n('skipped');
  }
}
const vitestTotals = existsSync(vitest) ? readJson(vitest) : null;

// ----------------------------------------------------------------------------- consolidación
const byRnf = {};
for (const m of metrics) (byRnf[m.rnf] ??= []).push(m);
for (const r of RNF) {
  const ms = byRnf[r.id] ?? [];
  r.metrics = ms;
  r.result = ms.length === 0 ? (r.state === 'Brecha' ? 'Brecha' : r.state) : ms.every((m) => m.pass) ? 'Cumple' : 'No cumple';
}
const generatedAt = new Date().toISOString();
mkdirSync(OUT, { recursive: true });
writeFileSync(join(OUT, 'evidencias-metricas.json'), JSON.stringify({ generatedAt, backendTests, vitest: vitestTotals && { total: vitestTotals.numTotalTests, passed: vitestTotals.numPassedTests, failed: vitestTotals.numFailedTests }, rnf: RNF }, null, 2));

const counts = RNF.reduce((acc, r) => ((acc[r.result] = (acc[r.result] ?? 0) + 1), acc), {});
const cell = (s) => String(s).replace(/\|/g, '\\|');
const value = (m) => `${fmt(m.value, m.unit === 'ms' ? 1 : 2)} ${m.unit}`;

let md = `# 5. Evidencias métricas\n\n`;
md += `> Generado por \`qa/scripts/build-evidence-report.mjs\` el ${generatedAt.slice(0, 10)} a partir de ejecuciones reales de la suite de calidad (ADR-0019). No editar a mano: volver a ejecutar la suite y el script.\n\n`;
md += `## 5.1 Resumen\n\n`;
md += `| Indicador | Valor |\n|---|---|\n`;
md += `| RNF del plan | ${RNF.length} |\n`;
for (const k of ['Cumple', 'No cumple', 'Pendiente Fase 2', 'No automatizable', 'Brecha']) md += `| RNF que ${k === 'Cumple' ? 'cumplen' : k === 'No cumple' ? 'no cumplen (no conformidad)' : `están en estado «${k}»`} | ${counts[k] ?? 0} |\n`;
md += `| Pruebas backend ejecutadas (JUnit) | ${backendTests.tests} (${backendTests.failures} fallidas) |\n`;
if (vitestTotals) md += `| Pruebas PWA ejecutadas (Vitest) | ${vitestTotals.numTotalTests} (${vitestTotals.numFailedTests} fallidas) |\n`;
md += `| Mediciones registradas | ${metrics.length} |\n\n`;
md += `Las fallas de JUnit que aparecen corresponden a las pruebas marcadas \`nonconformity\`: verifican el criterio literal del plan sobre comportamientos que hoy son distintos y solo corren con \`-Pquality\`.\n\n`;

md += `## 5.2 Mediciones por característica ISO/IEC 25010\n\n`;
for (const iso of Object.values(ISO)) {
  const rs = RNF.filter((r) => r.iso === iso);
  md += `### ${iso}\n\n| RNF | Métrica | Valor medido | Umbral del plan | Resultado | Fuente |\n|---|---|---|---|---|---|\n`;
  for (const r of rs) {
    if (r.metrics.length === 0) {
      md += `| ${r.id} | ${cell(r.name)} | — | — | ${r.result} | ${cell(r.note || '—')} |\n`;
      continue;
    }
    for (const m of r.metrics) md += `| ${r.id} | ${cell(m.metric)} | ${value(m)} | ${cell(m.threshold)} | ${m.pass ? 'Cumple' : '**No cumple**'} | ${cell(m.source)} |\n`;
  }
  md += `\n`;
}

md += `## 5.3 Detalle de cada medición\n\n`;
for (const m of [...metrics].sort((a, b) => a.rnf.localeCompare(b.rnf))) md += `- **${m.rnf} · ${m.metric}:** ${value(m)} (${m.pass ? 'cumple' : 'no cumple'} ${m.threshold}). ${m.detail}\n`;

md += `\n## 5.4 No conformidades detectadas\n\n| RNF | Hallazgo | Valor | Decisión pendiente |\n|---|---|---|---|\n`;
for (const m of metrics.filter((x) => !x.pass)) md += `| ${m.rnf} | ${cell(m.metric)} | ${value(m)} (umbral ${cell(m.threshold)}) | ${cell(m.detail)} |\n`;

md += `\n## 5.5 Métricas de proceso que no mide la suite\n\n`;
md += `| Métrica (§3.3 / §3.4) | Cómo se obtiene |\n|---|---|\n`;
for (const [k, v] of [
  ['Defectos por módulo, MTTR, cumplimiento de entregables', 'Gestor de incidencias (Jira) por sprint; las no conformidades de 5.4 se registran allí como defectos'],
  ['Tiempo de enrolamiento asistido (< 3 min, RNF-12)', 'Prueba de usuario en campo con timestamps de la PWA'],
  ['Eficacia en verificación comunitaria (> 95 %)', 'Consulta sobre catalog.host_profiles y aval de la asociación en producción'],
  ['Tasa de entrega efectiva del voucher en Gmail (> 99,5 %)', 'Registros del proveedor SMTP en producción (notification.notification_outbox.sent_at)'],
  ['Efectividad en procesamiento de pagos (> 95 %)', 'payments.payment_transactions en sandbox Wompi / producción'],
  ['Tiempo de dispersión bancaria (< 4 h hábiles), regla 72 h, validación offline', 'Se medirán con la Fase 2 (T-2.1 a T-2.13)'],
]) md += `| ${k} | ${v} |\n`;
writeFileSync(join(OUT, 'evidencias-metricas.md'), md);

let mx = `# Matriz de trazabilidad RNF → prueba\n\n> Plan de Gestión de la Calidad v1.00 · ADR-0019. Generada por \`qa/scripts/build-evidence-report.mjs\` (${generatedAt.slice(0, 10)}).\n\n`;
mx += `Estados: **Automatizada** (la suite verifica el criterio), **Parcial** (cubre parte del criterio), **Pendiente Fase 2** (el código aún no existe), **No automatizable** (medición manual u operativa), **Brecha** (no implementado y sin tarea asignada).\n\n`;
mx += `| RNF | Característica | Proceso | Requisito | Estado | Verificación | Resultado | Nota |\n|---|---|---|---|---|---|---|---|\n`;
for (const r of RNF) mx += `| ${r.id} | ${r.iso} | ${r.process} | ${cell(r.name)} | ${r.state} | ${cell(r.evidence)} | ${r.result} | ${cell(r.note || '')} |\n`;
mx += `\n## Cómo ejecutar\n\n| Suite | Comando | Evidencia |\n|---|---|---|\n`;
mx += `| Backend completo + cobertura | \`cd backend-api && ./mvnw verify\` | \`target/site/jacoco\`, \`target/quality-metrics\` |\n`;
mx += `| Solo calidad (incluye no conformidades) | \`cd backend-api && ./mvnw verify -Pquality\` | \`target/quality-metrics\` |\n`;
mx += `| PWA + cobertura | \`cd frontend-pwa && npm run test:quality\` | \`coverage/vitest-results.json\` |\n`;
mx += `| Bundle inicial | \`cd frontend-pwa && npm run build && npm run quality:bundle\` | \`coverage/bundle-budget.json\` |\n`;
mx += `| Carga | ver \`qa/k6/README.md\` | \`qa/k6/results/*.json\` |\n`;
mx += `| Informe | \`node qa/scripts/build-evidence-report.mjs\` | \`docs/quality/\` |\n`;
writeFileSync(join(OUT, 'matriz-trazabilidad-rnf.md'), mx);

console.log(`Evidencias: ${metrics.length} mediciones; ${JSON.stringify(counts)}`);
