// RNF-09 (Eficiencia · utilización de recursos), ADR-0019 §6: peso transferido del bundle inicial de la PWA.
// Suma, comprimido con gzip, todo lo que index.html descarga al abrir la app (scripts, modulepreload y
// hojas de estilo); las rutas con carga diferida (lazy) no cuentan. Uso: `npm run build && npm run quality:bundle`.
import { readFileSync, existsSync, mkdirSync, writeFileSync, statSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { gzipSync } from 'node:zlib';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const dist = join(root, 'dist');
const BUDGET_BYTES = 2.5 * 1024 * 1024; // plan: < 2,5 MB
const WARNING_BYTES = 500 * 1024; // ADR-0017: umbral de advertencia de Vite por chunk

if (!existsSync(join(dist, 'index.html'))) {
  console.error('No existe dist/index.html: ejecute `npm run build` antes.');
  process.exit(2);
}

const html = readFileSync(join(dist, 'index.html'), 'utf8');
const assets = new Set(['index.html']);
for (const match of html.matchAll(/<(?:script|link)\b[^>]*?(?:src|href)="\/?([^"]+)"/g)) {
  const path = match[1];
  if (!/^https?:/.test(path) && existsSync(join(dist, path))) assets.add(path);
}

const rows = [...assets].map((path) => {
  const raw = readFileSync(join(dist, path));
  return { path, raw: statSync(join(dist, path)).size, gzip: gzipSync(raw, { level: 9 }).length };
});
const total = rows.reduce((sum, r) => sum + r.gzip, 0);
const largest = Math.max(...rows.map((r) => r.raw));
const kb = (bytes) => `${(bytes / 1024).toFixed(1)} kB`;

for (const r of rows)
  console.log(`${r.path.padEnd(48)} ${kb(r.raw).padStart(10)}  gzip ${kb(r.gzip)}`);
console.log(`Total inicial transferido (gzip): ${kb(total)} — presupuesto ${kb(BUDGET_BYTES)}`);

const pass = total < BUDGET_BYTES;
const metric = {
  rnf: 'RNF-09',
  metric: 'Peso del bundle inicial comprimido',
  value: Number((total / 1024).toFixed(1)),
  unit: 'kB',
  threshold: '< 2560 kB (2,5 MB)',
  pass,
  detail: `${rows.length} recursos iniciales (gzip nivel 9); chunk más grande sin comprimir ${kb(largest)}${
    largest > WARNING_BYTES ? ' (supera el aviso de 500 kB de ADR-0017)' : ''
  }`,
};
mkdirSync(join(root, 'coverage'), { recursive: true });
writeFileSync(join(root, 'coverage', 'bundle-budget.json'), JSON.stringify(metric, null, 2));
process.exit(pass ? 0 : 1);
