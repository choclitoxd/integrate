interface SkipLinkProps {
  /** id del landmark <main> destino. */
  readonly targetId: string;
}

/** Primer elemento enfocable de cada página: salta la navegación (WCAG 2.4.1). */
export function SkipLink({ targetId }: SkipLinkProps) {
  return (
    <a
      href={`#${targetId}`}
      className="sr-only rounded bg-focus px-4 py-3 font-semibold text-ink focus:not-sr-only focus:fixed focus:top-2 focus:left-2 focus:z-50"
    >
      Saltar al contenido principal
    </a>
  );
}
