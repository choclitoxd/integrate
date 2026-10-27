import { useId, type ReactNode } from 'react';

import {
  joinPhone,
  lengthText,
  nationalDigits,
  PHONE_COUNTRIES,
  splitPhone,
  countryByDial,
} from '@/shared/lib';

import { FIELD_WRAPPER_CLASS, fieldBorderClass, INPUT_CLASS } from './fieldStyles';
import { Icon } from './Icon';
import { Select, type SelectOption } from './Select';

interface PhoneFieldProps {
  readonly label: string;
  /** Nombre del campo del número: `form.elements.namedItem(name)` lo enfoca al validar. */
  readonly name: string;
  /** Valor completo: "+57 3001234567" (el indicativo y el número nacional, separados por un espacio). */
  readonly value: string;
  readonly onChange: (value: string) => void;
  /** Ayuda permanente sobre el campo (para qué se pide el número). */
  readonly hint?: ReactNode;
  readonly error?: string | undefined;
  readonly disabled?: boolean;
}

const COUNTRY_OPTIONS: readonly SelectOption[] = PHONE_COUNTRIES.map((country) => ({
  value: country.dial,
  label: `${country.name} (+${country.dial})`,
  shortLabel: `+${country.dial}`,
}));

/**
 * Celular en dos partes: el indicativo del país (lista) y el número nacional. El largo máximo lo
 * fija el país elegido —10 dígitos en Colombia, 9 en Ecuador…—: lo que sobra no se admite al escribir
 * ni al pegar, y un número pegado completo ("+58 412 123 4567") cambia el indicativo solo. El valor
 * que sale sigue siendo un solo texto ("+57 3001234567") que valida `phoneSchema` y que el backend
 * recibe en E.164.
 */
export function PhoneField({
  label,
  name,
  value,
  onChange,
  hint,
  error,
  disabled,
}: PhoneFieldProps) {
  const id = useId();
  const hintId = `${id}-ayuda`;
  const lengthId = `${id}-largo`;
  const errorId = `${id}-error`;

  const { country, national } = splitPhone(value);
  const describedBy = [hint ? hintId : null, lengthId, error ? errorId : null]
    .filter(Boolean)
    .join(' ');

  return (
    <div>
      <label htmlFor={id} className="block font-semibold text-ink">
        {label}
      </label>
      {hint && (
        <p id={hintId} className="mt-0.5 text-sm text-ink-muted">
          {hint}
        </p>
      )}
      <div className="flex items-start gap-2">
        <div className="w-28 shrink-0">
          <Select
            label="Indicativo del país"
            hideLabel
            size="lg"
            menuWidth="wide"
            value={country.dial}
            options={COUNTRY_OPTIONS}
            disabled={disabled ?? false}
            invalid={Boolean(error)}
            onChange={(dial) => {
              const next = countryByDial(dial);
              // Al cambiar de país el número se recorta al máximo del nuevo, sin perder lo escrito.
              if (next) onChange(joinPhone(next, nationalDigits(national, next)));
            }}
          />
        </div>
        <div
          className={`${FIELD_WRAPPER_CLASS} min-w-0 flex-1 ${fieldBorderClass(Boolean(error))}`}
        >
          <input
            id={id}
            name={name}
            type="tel"
            inputMode="tel"
            autoComplete="tel-national"
            disabled={disabled ?? false}
            value={national}
            aria-invalid={error ? true : undefined}
            aria-describedby={describedBy}
            className={INPUT_CLASS}
            onChange={(event) => {
              const raw = event.target.value;
              // Un número pegado completo trae su indicativo: se separa y se cambia el país.
              if (raw.trim().startsWith('+')) {
                const parts = splitPhone(raw, country);
                onChange(joinPhone(parts.country, parts.national));
              } else {
                onChange(joinPhone(country, nationalDigits(raw, country)));
              }
            }}
          />
        </div>
      </div>
      <p id={lengthId} className="mt-1 text-sm text-ink-muted">
        {country.name}: {lengthText(country)}
        <span className="sr-only">, sin el indicativo</span>
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
