import { encode } from 'uqr';

/** Corrección L y margen de 4 módulos: misma especificación que el QR del PDF (ADR-0010 §6). */
export const QR_ECC = 'L';
export const QR_BORDER = 4;

export interface QrMatrix {
  readonly size: number;
  readonly data: readonly (readonly boolean[])[];
}

export function qrMatrix(value: string): QrMatrix {
  const qr = encode(value, { ecc: QR_ECC, border: QR_BORDER });
  return { size: qr.size, data: qr.data };
}

/** Un subtrazo por módulo oscuro: un solo `<path>` mantiene el SVG liviano. */
export function qrPath(data: QrMatrix['data']): string {
  let d = '';
  data.forEach((row, y) => {
    row.forEach((dark, x) => {
      if (dark) d += `M${String(x)} ${String(y)}h1v1h-1z`;
    });
  });
  return d;
}

/** "AB12CD34" → "AB12 CD34": más fácil de dictar y teclear en contingencia. */
export function groupShortCode(code: string): string {
  return `${code.slice(0, 4)} ${code.slice(4)}`;
}
