import type { ButtonHTMLAttributes } from 'react';

type Variant = 'primary' | 'secondary';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  readonly variant?: Variant;
}

const VARIANT_CLASSES: Record<Variant, string> = {
  primary: 'bg-brand text-on-brand hover:bg-brand-hover',
  secondary: 'border-2 border-brand bg-canvas text-brand hover:bg-brand-soft',
};

/** Botón base: objetivo táctil ≥ 44 px (WCAG 2.5.5) y estado deshabilitado perceptible. */
export function Button({
  variant = 'primary',
  className = '',
  type = 'button',
  ...props
}: ButtonProps) {
  return (
    <button
      type={type}
      className={`inline-flex min-h-11 items-center justify-center rounded-md px-5 font-semibold disabled:cursor-not-allowed disabled:opacity-60 ${VARIANT_CLASSES[variant]} ${className}`}
      {...props}
    />
  );
}
