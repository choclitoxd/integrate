import { useEffect, useId, useRef } from 'react';

import { Icon } from '@/shared/ui';

import { groupShortCode } from './matrix';
import { QrCode } from './QrCode';

interface QrFullscreenProps {
  readonly open: boolean;
  readonly onClose: () => void;
  readonly token: string;
  readonly shortCode: string;
  readonly validLabel: string;
}

/** Pantalla encendida mientras el guía escanea (Screen Wake Lock); se degrada si no existe. */
function useScreenWakeLock(active: boolean) {
  const sentinel = useRef<WakeLockSentinel | null>(null);
  useEffect(() => {
    if (!active || typeof navigator === 'undefined' || !('wakeLock' in navigator)) return;
    let cancelled = false;
    const acquire = async () => {
      try {
        const lock = await navigator.wakeLock.request('screen');
        if (cancelled) await lock.release();
        else sentinel.current = lock;
      } catch {
        // Ahorro de energía o permiso denegado: el QR sigue visible, solo sin bloqueo.
      }
    };
    // El sistema libera el bloqueo al ocultar la pestaña: se vuelve a pedir al regresar.
    const onVisible = () => {
      if (document.visibilityState === 'visible') void acquire();
    };
    void acquire();
    document.addEventListener('visibilitychange', onVisible);
    return () => {
      cancelled = true;
      document.removeEventListener('visibilitychange', onVisible);
      void sentinel.current?.release();
      sentinel.current = null;
    };
  }, [active]);
}

/**
 * QR a pantalla completa (ADR-0017 §3): `<dialog>` modal sobre papel blanco, QR al mayor cuadrado
 * que cabe, código corto grande. La entrada a pantalla completa la pide el clic que abre la vista.
 */
export function QrFullscreen({ open, onClose, token, shortCode, validLabel }: QrFullscreenProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  useScreenWakeLock(open);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  return (
    <dialog
      ref={dialogRef}
      aria-labelledby={titleId}
      onClose={() => {
        if (open) onClose();
        if (document.fullscreenElement) void document.exitFullscreen().catch(() => undefined);
      }}
      className="fixed inset-0 m-0 h-dvh max-h-none w-screen max-w-none bg-qr-paper p-0 text-ink not-open:hidden backdrop:bg-qr-paper"
    >
      {open && (
        <div className="mx-auto flex h-full max-w-md flex-col items-center px-4 pt-[max(1rem,env(safe-area-inset-top))] pb-[max(1rem,env(safe-area-inset-bottom))]">
          <div className="flex w-full items-center justify-between gap-3">
            <h2 id={titleId} className="text-lg font-bold">
              Voucher de tu reserva
            </h2>
            <button
              type="button"
              onClick={onClose}
              aria-label="Cerrar"
              className="inline-flex size-11 items-center justify-center border-0 border-none bg-transparent text-ink hover:opacity-70 active:opacity-50 focus:outline-none focus:ring-0 focus:shadow-none focus-visible:outline-none focus-visible:ring-0 focus-visible:shadow-none"
              style={{ outline: 'none', border: 'none', boxShadow: 'none' }}
            >
              <Icon name="close" size={24} />
            </button>
          </div>
          <div className="flex w-full flex-1 items-center justify-center py-2">
            <div className="w-[min(100%,calc(100dvh-14rem))]">
              <QrCode value={token} label={`Código QR del voucher, ${validLabel}`} />
            </div>
          </div>
          <p className="text-center text-sm text-ink-muted">Código de contingencia</p>
          <p className="text-center font-mono text-3xl font-bold tracking-widest tabular-nums">
            {groupShortCode(shortCode)}
          </p>
          <p className="mt-2 text-center text-sm text-ink-muted">
            {validLabel.charAt(0).toUpperCase() + validLabel.slice(1)}. Sube el brillo de la
            pantalla para facilitar la lectura.
          </p>
        </div>
      )}
    </dialog>
  );
}
