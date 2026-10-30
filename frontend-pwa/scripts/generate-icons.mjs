// Genera los íconos PNG de la PWA a partir de la misma marca de `public/favicon.svg` (ADR-0036 §5.4): Chrome/Android
// exige PNG de 192 y 512 px para ofrecer la instalación y iOS ignora un `apple-touch-icon` en SVG. Sin dependencias:
// rasteriza con supersampling y escribe el PNG con `node:zlib`. Uso: `node scripts/generate-icons.mjs`.
import { mkdirSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { crc32, deflateSync } from 'node:zlib';

const FOREST = [0x14, 0x53, 0x2d];
const SAND = [0xfe, 0xf3, 0xc7];
const SUPERSAMPLE = 4;

/** Triángulo de la marca en coordenadas relativas (0..1) del lienzo. */
const MARK = { a: [9 / 32, 23 / 32], b: [16 / 32, 9 / 32], c: [23 / 32, 23 / 32] };

function insideRoundedSquare(x, y, radius) {
  const dx = Math.max(radius - x, 0, x - (1 - radius));
  const dy = Math.max(radius - y, 0, y - (1 - radius));
  return dx * dx + dy * dy <= radius * radius;
}

function insideTriangle(x, y, { a, b, c }) {
  const d = (p, q, r) => (p[0] - r[0]) * (q[1] - r[1]) - (q[0] - r[0]) * (p[1] - r[1]);
  const p = [x, y];
  const d1 = d(p, a, b);
  const d2 = d(p, b, c);
  const d3 = d(p, c, a);
  const hasNeg = d1 < 0 || d2 < 0 || d3 < 0;
  const hasPos = d1 > 0 || d2 > 0 || d3 > 0;
  return !(hasNeg && hasPos);
}

/**
 * @param {number} size lado en píxeles
 * @param {{ radius: number, scale: number }} look `radius` redondea el fondo (0 = a sangre, para íconos maskable e iOS)
 *   y `scale` reduce la marca para dejarla dentro de la zona segura de un ícono maskable (80 % central).
 */
function render(size, { radius, scale }) {
  const raw = Buffer.alloc(size * (size * 4 + 1));
  for (let py = 0; py < size; py++) {
    const row = py * (size * 4 + 1);
    raw[row] = 0; // filtro «None»
    for (let px = 0; px < size; px++) {
      let bg = 0;
      let mark = 0;
      for (let sy = 0; sy < SUPERSAMPLE; sy++) {
        for (let sx = 0; sx < SUPERSAMPLE; sx++) {
          const x = (px + (sx + 0.5) / SUPERSAMPLE) / size;
          const y = (py + (sy + 0.5) / SUPERSAMPLE) / size;
          if (radius === 0 || insideRoundedSquare(x, y, radius)) bg++;
          const sx2 = 0.5 + (x - 0.5) / scale;
          const sy2 = 0.5 + (y - 0.5) / scale;
          if (insideTriangle(sx2, sy2, MARK)) mark++;
        }
      }
      const samples = SUPERSAMPLE * SUPERSAMPLE;
      const coverage = bg / samples;
      const markShare = bg === 0 ? 0 : Math.min(1, mark / bg);
      const offset = row + 1 + px * 4;
      for (let channel = 0; channel < 3; channel++) {
        raw[offset + channel] = Math.round(
          FOREST[channel] * (1 - markShare) + SAND[channel] * markShare,
        );
      }
      raw[offset + 3] = Math.round(coverage * 255);
    }
  }
  return raw;
}

function chunk(type, data) {
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const out = Buffer.alloc(body.length + 8);
  out.writeUInt32BE(data.length, 0);
  body.copy(out, 4);
  out.writeUInt32BE(crc32(body) >>> 0, body.length + 4);
  return out;
}

function png(size, look) {
  const header = Buffer.alloc(13);
  header.writeUInt32BE(size, 0);
  header.writeUInt32BE(size, 4);
  header[8] = 8; // 8 bits por canal
  header[9] = 6; // RGBA
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', header),
    chunk('IDAT', deflateSync(render(size, look), { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

const publicDir = resolve(fileURLToPath(new URL('.', import.meta.url)), '..', 'public');
mkdirSync(publicDir, { recursive: true });

const icons = [
  // «any»: fondo con esquinas redondeadas como el favicon (6/32).
  ['icon-192.png', 192, { radius: 6 / 32, scale: 1 }],
  ['icon-512.png', 512, { radius: 6 / 32, scale: 1 }],
  // «maskable»: a sangre y marca dentro del 80 % central (zona segura que el sistema nunca recorta).
  ['icon-maskable-512.png', 512, { radius: 0, scale: 0.8 }],
  // iOS redondea por su cuenta: a sangre y sin transparencia.
  ['apple-touch-icon.png', 180, { radius: 0, scale: 0.9 }],
];

for (const [name, size, look] of icons) {
  const file = join(publicDir, name);
  writeFileSync(file, png(size, look));
  console.log(`${name} (${size}×${size})`);
}
