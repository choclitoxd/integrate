import { toTitleCase } from '@/shared/lib';
import { normalizePlaceName } from '@/shared/maps/places';
import { Button } from '@/shared/ui';

import type { DetectionStatus } from './locationDetection';

interface LocationSummaryProps {
  readonly status: DetectionStatus;
  readonly municipality: string | undefined;
  readonly department: string | undefined;
  readonly vereda: string | undefined;
  readonly confirmed: boolean;
  /** Municipio que nombra la dirección escrita y que no es el detectado, si lo hay. */
  readonly mismatch: string | undefined;
  readonly onConfirm: () => void;
  readonly onChange: () => void;
}

/** Resumen de lo que Google detectó en el punto, con confirmación visible (ADR-0056, Fase 3). */
export function LocationSummary({
  status,
  municipality,
  department,
  vereda,
  confirmed,
  mismatch,
  onConfirm,
  onChange,
}: LocationSummaryProps) {
  if (status === 'idle') return null;
  if (status === 'detecting') {
    return (
      <p role="status" className="text-sm text-ink-muted">
        Detectando el municipio…
      </p>
    );
  }
  if (status === 'unresolved') {
    return (
      <p role="status" className="text-sm font-semibold text-ink">
        No pudimos identificar el municipio con este punto. Elígelo en la lista.
      </p>
    );
  }
  const place = [
    municipality && toTitleCase(municipality),
    // Bogotá es municipio y departamento a la vez: no se repite el nombre.
    department &&
      normalizePlaceName(department) !== normalizePlaceName(municipality ?? '') &&
      toTitleCase(department),
    vereda && `vereda ${vereda}`,
  ]
    .filter(Boolean)
    .join(', ');
  const end = place.endsWith('.') ? '' : '.';
  return (
    <div className="grid gap-2 rounded-lg border-2 border-border-subtle p-3">
      <p role="status" className="text-sm font-semibold text-ink">
        {confirmed ? `Municipio confirmado: ${place}${end}` : `Detectamos: ${place}${end}`}
      </p>
      {!confirmed && (
        <div className="grid grid-cols-2 gap-3">
          <Button type="button" onClick={onConfirm} className="min-h-11">
            Es correcto
          </Button>
          <Button type="button" variant="secondary" onClick={onChange} className="min-h-11">
            Cambiar
          </Button>
        </div>
      )}
      {mismatch && (
        <p role="alert" className="text-sm font-semibold text-danger-text">
          La dirección menciona {toTitleCase(mismatch)}, pero el punto está en{' '}
          {municipality ? toTitleCase(municipality) : 'otro municipio'}. Revisa antes de guardar.
        </p>
      )}
    </div>
  );
}
