export type ButtonVariant = 'primary' | 'secondary' | 'quiet';

const BASE =
  'inline-flex min-h-11 min-w-11 items-center justify-center gap-2 rounded-lg px-5 font-semibold transition-colors duration-150 disabled:cursor-not-allowed';

/**
 * El estado deshabilitado cambia de superficie en lugar de bajar la opacidad, para conservar el
 * contraste del texto bajo luz solar.
 */
const VARIANT_CLASSES: Record<ButtonVariant, string> = {
  primary:
    'bg-brand text-on-brand hover:bg-brand-hover active:bg-brand-active disabled:bg-surface-subtle disabled:text-ink-muted',
  secondary:
    'border-2 border-brand bg-canvas text-brand hover:bg-brand-soft disabled:border-border-subtle disabled:text-ink-muted disabled:hover:bg-canvas',
  quiet: 'text-brand underline-offset-4 hover:underline disabled:text-ink-muted',
};

/** Clases del botón, reutilizables en enlaces (<Link>) con aspecto de botón. */
export function buttonClassName(variant: ButtonVariant = 'primary', extra = ''): string {
  return `${BASE} ${VARIANT_CLASSES[variant]} ${extra}`;
}
