import { encode } from 'uqr';
import { describe, expect, it } from 'vitest';

import { decodeQrFromImageData } from './qrDecoder';

/** Rasteriza un QR en RGBA (negro sobre blanco, zona de silencio de 4 módulos) como lo daría un fotograma de cámara. */
function renderQr(text: string, scale: number) {
  const { data, size } = encode(text, { ecc: 'L' });
  const quiet = 4;
  const dimension = (size + quiet * 2) * scale;
  const pixels = new Uint8ClampedArray(dimension * dimension * 4).fill(255);
  for (let row = 0; row < size; row++) {
    for (let col = 0; col < size; col++) {
      if (!data[row]?.[col]) continue;
      for (let dy = 0; dy < scale; dy++) {
        for (let dx = 0; dx < scale; dx++) {
          const y = (row + quiet) * scale + dy;
          const x = (col + quiet) * scale + dx;
          const offset = (y * dimension + x) * 4;
          pixels[offset] = 0;
          pixels[offset + 1] = 0;
          pixels[offset + 2] = 0;
        }
      }
    }
  }
  return { pixels, dimension };
}

describe('decodeQrFromImageData (respaldo por software sin BarcodeDetector)', () => {
  it('lee un token del tamaño de un voucher RS256 (~650 caracteres)', async () => {
    const token = `eyJhbGciOiJSUzI1NiJ9.${'a1B2c3D4'.repeat(70)}.${'Zz9-_'.repeat(60)}`;
    const { pixels, dimension } = renderQr(token, 4);

    await expect(decodeQrFromImageData(pixels, dimension, dimension)).resolves.toBe(token);
  });

  it('devuelve null cuando el fotograma no trae ningún QR', async () => {
    const blank = new Uint8ClampedArray(200 * 200 * 4).fill(255);

    await expect(decodeQrFromImageData(blank, 200, 200)).resolves.toBeNull();
  });
});
