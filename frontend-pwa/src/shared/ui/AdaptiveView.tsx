import { Suspense, type ComponentType, type ReactNode } from 'react';

import { useFormFactor } from '@/shared/lib';

interface AdaptiveViewProps {
  /** Vista de escritorio, normalmente `lazy()` para que el teléfono no descargue su código. */
  readonly desktop: ComponentType;
  /** Vista móvil: la de siempre y la única en teléfonos y tablets. */
  readonly children: ReactNode;
}

/**
 * Elige la vista según el factor de forma (ADR-0020 §3). Recibe las vistas por props, así que
 * `shared/` no conoce ninguna feature. Ambas comparten consultas, formularios y API.
 */
export function AdaptiveView({ desktop: Desktop, children }: AdaptiveViewProps) {
  if (useFormFactor() === 'mobile') return children;
  return (
    <Suspense
      fallback={
        <p role="status" className="py-12 text-center font-semibold text-ink-muted">
          Cargando…
        </p>
      }
    >
      <Desktop />
    </Suspense>
  );
}
