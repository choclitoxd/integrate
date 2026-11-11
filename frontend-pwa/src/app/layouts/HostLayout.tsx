import { useLocation } from 'react-router';

import { ZoneBlockedPage } from '../pages/ZoneBlockedPage';
import { AppShell } from './AppShell';
import { useRoleNavigation } from './useRoleNavigation';
import { useZoneStatus } from './zoneStatus';

export function HostLayout() {
  const navItems = useRoleNavigation();
  const { host } = useZoneStatus();
  const { pathname } = useLocation();
  // ADR-0070: suspendido o revocado ve un aviso en lugar del panel; «Mi cuenta» siempre queda disponible.
  const blocked = host !== undefined && host !== 'ACTIVE' && !pathname.endsWith('/cuenta');
  return (
    <AppShell navLabel="Navegación principal" navItems={navItems} homeTo="/">
      {blocked ? <ZoneBlockedPage zone="host" status={host} /> : undefined}
    </AppShell>
  );
}
