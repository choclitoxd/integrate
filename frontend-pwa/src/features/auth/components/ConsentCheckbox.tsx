import { useId } from 'react';

import type { ConsentPurpose } from '@/shared/session';
import { Icon } from '@/shared/ui';

import { PURPOSE_COPY } from '../purposes';

interface ConsentCheckboxProps {
  readonly purpose: ConsentPurpose;
  readonly checked: boolean;
  readonly onChange: (checked: boolean) => void;
  readonly required?: boolean;
  readonly error?: string | undefined;
  readonly name?: string;
}

/**
 * Una finalidad por casilla, sin marcar de antemano (autorización expresa y granular).
 * Toda la tarjeta es el objetivo táctil.
 */
export function ConsentCheckbox({
  purpose,
  checked,
  onChange,
  required = false,
  error,
  name,
}: ConsentCheckboxProps) {
  const bodyId = useId();
  const errorId = useId();
  const copy = PURPOSE_COPY[purpose];
  return (
    <div>
      <label
        className={`flex cursor-pointer items-start gap-3 rounded-xl border-2 px-4 py-3 has-checked:border-brand has-checked:bg-brand-soft ${
          error ? 'border-danger-border' : 'border-border'
        }`}
      >
        <input
          type="checkbox"
          name={name ?? purpose}
          checked={checked}
          onChange={(event) => {
            onChange(event.target.checked);
          }}
          aria-describedby={[bodyId, error ? errorId : null].filter(Boolean).join(' ')}
          aria-invalid={error ? true : undefined}
          className="mt-0.5 size-6 shrink-0 accent-brand"
        />
        <span className="font-semibold text-ink">
          Autorizo el tratamiento de: {copy.title}
          {required ? ' (obligatorio)' : ' (opcional)'}
        </span>
      </label>
      <p id={bodyId} className="mt-1.5 pl-1 text-sm text-ink-muted">
        {copy.body}
      </p>
      {error && (
        <p
          id={errorId}
          className="mt-1 flex items-start gap-1.5 text-sm font-semibold text-danger-text"
        >
          <Icon name="alert" size={18} className="mt-px" />
          {error}
        </p>
      )}
    </div>
  );
}
