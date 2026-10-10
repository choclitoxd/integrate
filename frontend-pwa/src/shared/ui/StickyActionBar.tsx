import type { ReactNode } from 'react';

interface StickyActionBarProps {
  /** Resumen a la izquierda (precio, total, personas). */
  readonly summary?: ReactNode;
  /** Acción primaria de la vista, en la zona del pulgar (FE-DIR-001, Directiva 1.4). */
  readonly children: ReactNode;
  /** Nota bajo la barra, p. ej. por qué la acción está inhabilitada. */
  readonly note?: ReactNode;
}

/**
 * Barra de acción fija inferior, alineada con la columna max-w-md. Las vistas que la usan
 * marcan su ruta como `flow` para que el shell oculte la navegación inferior.
 */
export function StickyActionBar({ summary, children, note }: StickyActionBarProps) {
  return (
    <div className="fixed inset-x-0 bottom-0 z-30">
      <div className="mx-auto w-full max-w-md border-t-2 border-border-strong bg-surface-elevated px-4 pt-3 pb-[max(0.75rem,env(safe-area-inset-bottom))]">
        <div className="flex items-center justify-between gap-4">
          {summary && <div className="min-w-0 flex-1">{summary}</div>}
          <div className={summary ? 'shrink-0' : 'flex-1 [&>*]:w-full'}>{children}</div>
        </div>
        {note && <div className="mt-2 text-sm text-ink-muted">{note}</div>}
      </div>
    </div>
  );
}
