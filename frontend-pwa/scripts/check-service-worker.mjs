// Verifica que `dist/sw.js` salga con el precache inyectado (ADR-0036 §5). Un Service Worker sin manifiesto no falla al
// compilar ni al registrarse, pero no precachea nada y la PWA deja de abrir en modo avión: este control lo detecta en
// el `build`. Uso: `node scripts/check-service-worker.mjs [carpeta-dist]`.
import { existsSync, readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(fileURLToPath(new URL('.', import.meta.url)), '..');
const dist = resolve(root, process.argv[2] ?? 'dist');
const swPath = join(dist, 'sw.js');

const problems = [];
if (!existsSync(swPath)) {
  problems.push('no existe dist/sw.js');
} else {
  const sw = readFileSync(swPath, 'utf8');
  for (const placeholder of ['__PRECACHE_MANIFEST__', '__WB_MANIFEST']) {
    if (sw.includes(placeholder)) problems.push(`quedó sin sustituir ${placeholder}`);
  }
  for (const expected of ['/index.html', '/manifest.webmanifest', '/assets/']) {
    if (!sw.includes(expected)) problems.push(`el precache no contiene ${expected}`);
  }
  if (sw.includes('/manual/'))
    problems.push('el precache incluye /manual/ (debe quedar fuera de la cuota offline)');
  if (!/\/assets\/[^"'`]+\.js/.test(sw))
    problems.push('el precache no contiene ningún chunk JS de /assets/');
}

if (problems.length > 0) {
  console.error('Service Worker inválido:\n - ' + problems.join('\n - '));
  process.exit(1);
}
console.log('Service Worker: precache inyectado correctamente.');
