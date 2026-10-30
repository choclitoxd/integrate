import { useEffect } from 'react';

import { useHttpClient } from '@/shared/api';

import { AppShell, type NavItem } from './AppShell';

const NAV_ITEMS: readonly NavItem[] = [
  { to: '/gestor', label: 'Jornada', icon: 'route' },
  { to: '/gestor/validar', label: 'Validar QR', icon: 'qr' },
  { to: '/gestor/anfitriones', label: 'Anfitriones', icon: 'users' },
  { to: '/cuenta', label: 'Mi cuenta', icon: 'user' },
];

/** Shell del Gestor de Enlace; la ruta exige ROLE_LINK_MANAGER (ADR-0013 §6, ADR-0037 §5.3). */
export function ManagerLayout() {
  const http = useHttpClient();

  useEffect(() => {
    const triggerFlush = () => {
      if (typeof navigator !== 'undefined' && !navigator.onLine) {
        return;
      }
      void import('@/features/offline').then(({ flushSyncOutbox }) => flushSyncOutbox(http));
    };

    const handleVisibility = () => {
      if (typeof document !== 'undefined' && document.visibilityState === 'visible') {
        triggerFlush();
      }
    };

    const handleSwMessage = (event: MessageEvent<unknown>) => {
      if (
        typeof event.data === 'object' &&
        event.data !== null &&
        (event.data as { type?: string }).type === 'SW_SYNC_REQUESTED'
      ) {
        triggerFlush();
      }
    };

    window.addEventListener('online', triggerFlush);
    document.addEventListener('visibilitychange', handleVisibility);
    const swContainer = (globalThis.navigator as { serviceWorker?: EventTarget }).serviceWorker;
    swContainer?.addEventListener('message', handleSwMessage as EventListener);

    return () => {
      window.removeEventListener('online', triggerFlush);
      document.removeEventListener('visibilitychange', handleVisibility);
      swContainer?.removeEventListener('message', handleSwMessage as EventListener);
    };
  }, [http]);

  return <AppShell navLabel="Navegación del gestor" navItems={NAV_ITEMS} homeTo="/gestor" />;
}
