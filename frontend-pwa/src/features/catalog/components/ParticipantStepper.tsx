import { useId } from 'react';

import { Icon } from '@/shared/ui';

interface ParticipantStepperProps {
  readonly value: number;
  readonly max: number;
  readonly onChange: (value: number) => void;
}

const STEP_BUTTON =
  'inline-flex size-12 items-center justify-center rounded-lg border-2 border-brand bg-canvas text-brand hover:bg-brand-soft disabled:border-border-subtle disabled:bg-surface-subtle disabled:text-ink-muted';

/** Número de personas del grupo (1 … tope de la franja); el valor se anuncia al cambiar. */
export function ParticipantStepper({ value, max, onChange }: ParticipantStepperProps) {
  const labelId = useId();
  return (
    <div role="group" aria-labelledby={labelId} className="flex items-center justify-between gap-4">
      <div>
        <p id={labelId} className="font-semibold text-ink">
          Personas
        </p>
        <p className="text-sm text-ink-muted">Hasta {max}</p>
      </div>
      <div className="flex items-center gap-2">
        <button
          type="button"
          aria-label="Quitar una persona"
          disabled={value <= 1}
          onClick={() => {
            onChange(value - 1);
          }}
          className={STEP_BUTTON}
        >
          <Icon name="minus" size={22} />
        </button>
        <output
          aria-live="polite"
          className="w-10 text-center text-2xl font-bold text-ink tabular-nums"
        >
          {value}
        </output>
        <button
          type="button"
          aria-label="Agregar una persona"
          disabled={value >= max}
          onClick={() => {
            onChange(value + 1);
          }}
          className={STEP_BUTTON}
        >
          <Icon name="plus" size={22} />
        </button>
      </div>
    </div>
  );
}
