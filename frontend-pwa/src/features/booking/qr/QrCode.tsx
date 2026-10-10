import { useMemo } from 'react';

import { qrMatrix, qrPath } from './matrix';

interface QrCodeProps {
  readonly value: string;
  readonly label: string;
  readonly className?: string;
}

/**
 * QR del voucher como SVG vectorial (ADR-0017 §2): nítido a cualquier tamaño, negro y blanco puros
 * desde los tokens `qr-ink`/`qr-paper`, con nombre accesible.
 */
export function QrCode({ value, label, className = '' }: QrCodeProps) {
  const { size, d } = useMemo(() => {
    const matrix = qrMatrix(value);
    return { size: matrix.size, d: qrPath(matrix.data) };
  }, [value]);
  return (
    <svg
      role="img"
      aria-label={label}
      viewBox={`0 0 ${String(size)} ${String(size)}`}
      shapeRendering="crispEdges"
      className={`block aspect-square h-auto w-full ${className}`}
    >
      <rect width={size} height={size} className="fill-qr-paper" />
      <path d={d} className="fill-qr-ink" />
    </svg>
  );
}
