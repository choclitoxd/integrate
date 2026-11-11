import { accountPathFor, hasRole, type SessionState } from '@/shared/session';

import type { NavItem } from './shell';
import type { ZoneStatus, ZoneStatuses } from './zoneStatus';

/** Sin dato (se consulta o falló) se ofrece como siempre; solo ACTIVE opera (ADR-0069, ADR-0070). */
const operates = (status: ZoneStatus | undefined) => status === undefined || status === 'ACTIVE';

export function roleNavigation(
  session: SessionState,
  zones: ZoneStatuses = {},
): readonly NavItem[] {
  return [
    { to: '/', label: 'Explorar', icon: 'compass' },
    ...(hasRole(session, 'ROLE_TOURIST')
      ? [{ to: '/mis-experiencias', label: 'Mis experiencias', icon: 'ticket' } as const]
      : []),
    // Las entradas del anfitrión viven aquí y no en HostLayout: así siguen visibles al volver a Explorar.
    ...(hasRole(session, 'ROLE_HOST') && operates(zones.host)
      ? ([
          { to: '/anfitrion', label: 'Anfitrión', icon: 'calendar', group: 'Anfitrión' },
          { to: '/anfitrion/metricas', label: 'Mis métricas', icon: 'chart', group: 'Anfitrión' },
          {
            to: '/anfitrion/experiencias',
            label: 'Experiencias del anfitrión',
            icon: 'mountain',
            group: 'Anfitrión',
          },
        ] as const)
      : []),
    // Igual que las del anfitrión: siguen visibles al volver a Explorar.
    ...(hasRole(session, 'ROLE_LINK_MANAGER') && operates(zones.gestor)
      ? ([
          { to: '/gestor', label: 'Jornada', icon: 'route', group: 'Gestor de enlace' },
          { to: '/gestor/validar', label: 'Validar QR', icon: 'qr', group: 'Gestor de enlace' },
          {
            to: '/gestor/anfitriones',
            label: 'Anfitriones',
            icon: 'users',
            group: 'Gestor de enlace',
          },
        ] as const)
      : []),
    session.status === 'authenticated'
      ? { to: accountPathFor(session), label: 'Mi cuenta', icon: 'user' }
      : { to: '/ingresar', label: 'Ingresar', icon: 'user' },
  ];
}
