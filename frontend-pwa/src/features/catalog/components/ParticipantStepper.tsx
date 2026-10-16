import { useId, useState } from 'react';

import { Icon } from '@/shared/ui';

interface ParticipantStepperProps {
  readonly value: number;
  readonly max: number;
  readonly onChange: (value: number) => void;
}

const STEP_BUTTON =
  'inline-flex size-12 items-center justify-center rounded-lg border-2 border-brand bg-canvas text-brand hover:bg-brand-soft disabled:border-border-subtle disabled:bg-surface-subtle disabled:text-ink-muted';

/** Número de personas del grupo (1 … tope de la franja); el valor se anuncia al cambiar y puede editarse directamente. */
export function ParticipantStepper({ value, max, onChange }: ParticipantStepperProps) {
  const labelId = useId();
  const inputId = useId();
  const [draft, setDraft] = useState<string | null>(null);

  function handleChange(event: React.ChangeEvent<HTMLInputElement>) {
    const clean = event.target.value.replace(/\D/g, '');
    setDraft(clean);
    if (clean !== '') {
      const num = Number.parseInt(clean, 10);
      if (num >= 1 && num <= max) {
        onChange(num);
      } else if (num > max) {
        onChange(max);
        setDraft(String(max));
      }
    }
  }

  function handleBlur() {
    const num = Number.parseInt(draft ?? '', 10);
    if (Number.isNaN(num) || num < 1) {
      onChange(1);
    } else if (num > max) {
      onChange(max);
    } else {
      onChange(num);
    }
    setDraft(null);
  }

  function handleKeyDown(event: React.KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'ArrowUp') {
      event.preventDefault();
      if (value < max) {
        const next = value + 1;
        onChange(next);
        setDraft(String(next));
      }
    } else if (event.key === 'ArrowDown') {
      event.preventDefault();
      if (value > 1) {
        const prev = value - 1;
        onChange(prev);
        setDraft(String(prev));
      }
    } else if (event.key === 'Enter') {
      event.currentTarget.blur();
    }
  }

  const displayedValue = draft ?? String(value);

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
            const next = Math.max(1, value - 1);
            onChange(next);
            setDraft(null);
          }}
          className={STEP_BUTTON}
        >
          <Icon name="minus" size={22} />
        </button>
        <output aria-live="polite" className="sr-only">
          {value} {value === 1 ? 'persona' : 'personas'}
        </output>
        <input
          id={inputId}
          type="text"
          inputMode="numeric"
          pattern="[0-9]*"
          role="spinbutton"
          aria-valuenow={value}
          aria-valuemin={1}
          aria-valuemax={max}
          aria-label="Número de personas"
          value={displayedValue}
          onChange={handleChange}
          onBlur={handleBlur}
          onFocus={(event) => {
            setDraft(String(value));
            event.currentTarget.select();
          }}
          onClick={(event) => {
            event.currentTarget.select();
          }}
          onKeyDown={handleKeyDown}
          className="h-12 w-14 rounded-lg border-2 border-border bg-canvas px-1 text-center text-2xl font-bold text-ink tabular-nums transition-colors hover:border-brand focus:border-brand focus:outline-none focus-visible:outline-none focus-visible:shadow-none"
        />
        <button
          type="button"
          aria-label="Agregar una persona"
          disabled={value >= max}
          onClick={() => {
            const next = Math.min(max, value + 1);
            onChange(next);
            setDraft(null);
          }}
          className={STEP_BUTTON}
        >
          <Icon name="plus" size={22} />
        </button>
      </div>
    </div>
  );
}
