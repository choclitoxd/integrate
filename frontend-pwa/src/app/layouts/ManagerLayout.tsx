import { useEffect } from 'react';
import { useLocation } from 'react-router';

import { useHttpClient } from '@/shared/api';

import { ZoneBlockedPage } from '../pages/ZoneBlockedPage';
import { AppShell } from './AppShell';
import { useRoleNavigation } from './useRoleNavigation';
import { useZoneStatus } from './zoneStatus';

/** Shell del Gestor de Enlace; la ruta exige ROLE_LINK_MANAGER (ADR-0013 §6, ADR-0037 §5.3). */
export function ManagerLayout() {
  const http = useHttpClient();
  const navItems = useRoleNavigation();
  const { gestor } = useZoneStatus();
  const { pathname } = useLocation();
  // ADR-0069: con la acreditación suspendida o revocada ve un aviso en lugar del panel; «Mi cuenta» sigue disponible.
  const blocked = gestor !== undefined && gestor !== 'ACTIVE' && !pathname.endsWith('/cuenta');

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

  return (
    <AppShell navLabel="Navegación del gestor" navItems={navItems} homeTo="/gestor">
      {blocked ? <ZoneBlockedPage zone="gestor" status={gestor} /> : undefined}
    </AppShell>
  );
}
