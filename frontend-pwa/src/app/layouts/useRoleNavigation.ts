import { useMemo } from 'react';

import { useSession } from '@/shared/session';

import { roleNavigation } from './roleNavigation';
import type { NavItem } from './shell';
import { useZoneStatus } from './zoneStatus';

/** Navegación por rol que oculta las herramientas de quien está suspendido o revocado (ADR-0069, ADR-0070). */
export function useRoleNavigation(): readonly NavItem[] {
  const session = useSession();
  const zones = useZoneStatus();
  return useMemo(() => roleNavigation(session, zones), [session, zones]);
}
