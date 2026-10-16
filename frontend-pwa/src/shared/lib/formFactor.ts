import { useSyncExternalStore } from 'react';

export type FormFactor = 'mobile' | 'desktop';

/**
 * ADR-0020 §2: escritorio = pantalla ancha manejada con ratón. Se decide por capacidades de entrada,
 * no por User-Agent; tablets y teléfonos (puntero grueso) reciben la vista móvil.
 */
export const DESKTOP_MEDIA_QUERY = '(min-width: 1024px) and (hover: hover) and (pointer: fine)';

function desktopQuery(): MediaQueryList | undefined {
  // jsdom y navegadores muy antiguos no implementan matchMedia: se asume móvil.
  return typeof window.matchMedia === 'function'
    ? window.matchMedia(DESKTOP_MEDIA_QUERY)
    : undefined;
}

function subscribe(onChange: () => void) {
  const list = desktopQuery();
  list?.addEventListener('change', onChange);
  return () => {
    list?.removeEventListener('change', onChange);
  };
}

/**
 * Vista que debe mostrarse según el dispositivo. Es síncrona en el primer render (sin parpadeo) y
 * reacciona al redimensionar la ventana o al conectar un ratón.
 */
export function useFormFactor(): FormFactor {
  return useSyncExternalStore(
    subscribe,
    () => (desktopQuery()?.matches ? 'desktop' : 'mobile'),
    () => 'mobile' as const,
  );
}
