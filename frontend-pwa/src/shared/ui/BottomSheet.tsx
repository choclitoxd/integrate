import { useEffect, useId, useRef, useState, type ReactNode } from 'react';

import { Icon } from './Icon';

interface BottomSheetProps {
  readonly open: boolean;
  readonly onClose: () => void;
  readonly title: string;
  readonly children: ReactNode;
  /** Acciones fijas al pie de la hoja (p. ej. "Limpiar" y "Ver resultados"). */
  readonly footer?: ReactNode;
  /**
   * `false` para decisiones que no se pueden posponer (re-consentimiento, ADR-0013 §5): sin botón
   * de cerrar, sin cierre por toque fuera y con Esc anulado. La hoja solo se cierra desde el pie.
   */
  readonly dismissible?: boolean;
}

/**
 * Hoja inferior modal sobre <dialog> nativo (ADR-0012 §6): showModal() atrapa el foco, deja el
 * fondo inerte, cierra con Esc y devuelve el foco al disparador. El contenido solo se monta
 * abierto. `onClose` solo se llama cuando cierra el usuario (Esc, toque fuera, botón Cerrar).
 * Al cerrar, anima el descenso hacia abajo (260 ms) de forma simétrica a la apertura.
 */
export function BottomSheet({
  open,
  onClose,
  title,
  children,
  footer,
  dismissible = true,
}: BottomSheetProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const closingFromProp = useRef(false);
  const titleId = useId();
  const [closing, setClosing] = useState(false);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (open && !dialog.open) {
      setClosing(false);
      dialog.showModal();
    }
    if (!open && dialog.open) {
      // Cierre pedido por el padre: no es una decisión del usuario y no debe disparar onClose.
      closingFromProp.current = true;
      setClosing(false);
      dialog.close();
    }
  }, [open]);

  const requestClose = () => {
    if (closing) return;
    if (
      import.meta.env.MODE === 'test' ||
      (typeof window !== 'undefined' &&
        typeof window.matchMedia === 'function' &&
        window.matchMedia('(prefers-reduced-motion: reduce)').matches)
    ) {
      onClose();
      return;
    }
    setClosing(true);
  };

  const handleAnimationEnd = (event: React.AnimationEvent<HTMLDialogElement>) => {
    if (closing && event.target === dialogRef.current) {
      setClosing(false);
      onClose();
    }
  };

  useEffect(() => {
    if (!closing) return;
    const timer = window.setTimeout(() => {
      setClosing(false);
      onClose();
    }, 280);
    return () => {
      window.clearTimeout(timer);
    };
  }, [closing, onClose]);

  return (
    <dialog
      ref={dialogRef}
      aria-labelledby={titleId}
      onAnimationEnd={handleAnimationEnd}
      onClose={() => {
        if (closingFromProp.current) {
          closingFromProp.current = false;
        } else {
          onClose();
        }
      }}
      onCancel={(event) => {
        event.preventDefault();
        if (dismissible) {
          requestClose();
        }
      }}
      // Cierre por toque fuera de la hoja donde el navegador lo soporta (light dismiss).
      {...{ closedby: dismissible ? 'any' : 'none' }}
      className={`sheet fixed inset-x-0 top-auto bottom-0 m-0 mx-auto flex max-h-[85dvh] w-full max-w-md flex-col rounded-t-2xl bg-surface-elevated p-0 text-ink not-open:hidden backdrop:bg-surface-inverse/60${
        closing ? ' closing' : ''
      }`}
    >
      {open && (
        <>
          <header className="flex items-center justify-between gap-4 border-b border-border-subtle py-2 pr-2 pl-5">
            <h2 id={titleId} className="text-lg font-bold">
              {title}
            </h2>
            {dismissible ? (
              <button
                type="button"
                onClick={requestClose}
                aria-label="Cerrar"
                className="inline-flex size-11 items-center justify-center border-0 border-none bg-transparent text-ink hover:opacity-70 active:opacity-50 focus:outline-none focus:ring-0 focus:shadow-none focus-visible:outline-none focus-visible:ring-0 focus-visible:shadow-none"
                style={{ outline: 'none', border: 'none', boxShadow: 'none' }}
              >
                <Icon name="close" size={22} />
              </button>
            ) : (
              <span className="min-h-11" />
            )}
          </header>
          {/* `flex-auto` (basis: content), no `flex-1` (basis: 0%): con el <dialog> de altura automática,
              Safari/iOS toma la base 0% y deja el cuerpo casi sin altura (solo se veía el encabezado). */}
          <div className="min-h-0 flex-auto overflow-y-auto overscroll-contain px-5 py-5">
            {children}
          </div>
          {footer && (
            <footer className="flex gap-3 border-t border-border-subtle px-5 pt-3 pb-[max(0.75rem,env(safe-area-inset-bottom))]">
              {footer}
            </footer>
          )}
        </>
      )}
    </dialog>
  );
}
