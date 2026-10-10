import type { ButtonHTMLAttributes } from 'react';

import { buttonClassName, type ButtonVariant } from './buttonStyles';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  readonly variant?: ButtonVariant;
}

/** Botón base: objetivo táctil ≥ 44 px (WCAG 2.5.5) y estado deshabilitado perceptible. */
export function Button({
  variant = 'primary',
  className = '',
  type = 'button',
  ...props
}: ButtonProps) {
  return <button type={type} className={buttonClassName(variant, className)} {...props} />;
}
