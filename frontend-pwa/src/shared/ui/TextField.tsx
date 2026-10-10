import { useId, useState, type InputHTMLAttributes, type ReactNode } from 'react';

import { Icon } from './Icon';

interface TextFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'id'> {
  readonly label: string;
  /** Ayuda permanente bajo el campo (formato esperado, por qué se pide). */
  readonly hint?: ReactNode;
  /** Error de validación; marca el campo con aria-invalid y lo anuncia. */
  readonly error?: string | undefined;
  /** Contenido a la derecha del campo dentro del borde (p. ej. "Mostrar"). */
  readonly trailing?: ReactNode;
}

const INPUT_CLASS =
  'min-h-12 w-full min-w-0 bg-transparent px-3 text-base text-ink placeholder:text-ink-subtle focus-visible:outline-none';

/**
 * Campo de texto accesible: etiqueta visible, ayuda y error enlazados por aria-describedby, y
 * borde de error con icono además del color (FE-DIR-001, Directiva 2).
 */
export function TextField({
  label,
  hint,
  error,
  trailing,
  className = '',
  ...props
}: TextFieldProps) {
  const id = useId();
  const hintId = `${id}-ayuda`;
  const errorId = `${id}-error`;
  const describedBy = [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(' ');

  return (
    <div className={className}>
      <label htmlFor={id} className="block font-semibold text-ink">
        {label}
      </label>
      {hint && (
        <p id={hintId} className="mt-0.5 text-sm text-ink-muted">
          {hint}
        </p>
      )}
      <div
        className={`mt-1.5 flex items-center rounded-lg border-2 bg-canvas has-focus-visible:outline-3 has-focus-visible:outline-focus has-focus-visible:shadow-[0_0_0_6px_var(--color-focus-halo)] ${
          error ? 'border-danger-border' : 'border-border'
        }`}
      >
        <input
          id={id}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy || undefined}
          className={INPUT_CLASS}
          {...props}
        />
        {trailing}
      </div>
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

type PasswordFieldProps = Omit<TextFieldProps, 'type' | 'trailing'>;

/** Contraseña con "Mostrar/Ocultar" de 44 px: en campo se teclea con prisa y al sol. */
export function PasswordField(props: PasswordFieldProps) {
  const [visible, setVisible] = useState(false);
  return (
    <TextField
      {...props}
      type={visible ? 'text' : 'password'}
      trailing={
        <button
          type="button"
          aria-pressed={visible}
          onClick={() => {
            setVisible((value) => !value);
          }}
          className="mr-1 inline-flex min-h-11 min-w-11 shrink-0 items-center justify-center rounded-md px-2 text-sm font-semibold text-brand hover:bg-brand-soft"
        >
          {visible ? 'Ocultar' : 'Mostrar'}
          <span className="sr-only"> contraseña</span>
        </button>
      }
    />
  );
}
