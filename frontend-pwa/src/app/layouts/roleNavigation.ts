import { accountPathFor, hasRole, type SessionState } from '@/shared/session';

import type { NavItem } from './shell';

export function roleNavigation(session: SessionState): readonly NavItem[] {
  return [
    { to: '/', label: 'Explorar', icon: 'compass' },
    ...(hasRole(session, 'ROLE_TOURIST')
      ? [{ to: '/mis-experiencias', label: 'Mis experiencias', icon: 'ticket' } as const]
      : []),
    // Las entradas del anfitrión viven aquí y no en HostLayout: así siguen visibles al volver a Explorar.
    ...(hasRole(session, 'ROLE_HOST')
      ? ([
          { to: '/anfitrion', label: 'Anfitrión', icon: 'calendar' },
          { to: '/anfitrion/metricas', label: 'Mis métricas', icon: 'chart' },
          { to: '/anfitrion/experiencias', label: 'Experiencias del anfitrión', icon: 'mountain' },
        ] as const)
      : []),
    // Igual que las del anfitrión: siguen visibles al volver a Explorar.
    ...(hasRole(session, 'ROLE_LINK_MANAGER')
      ? ([
          { to: '/gestor', label: 'Jornada', icon: 'route' },
          { to: '/gestor/validar', label: 'Validar QR', icon: 'qr' },
          { to: '/gestor/anfitriones', label: 'Anfitriones', icon: 'users' },
        ] as const)
      : []),
    session.status === 'authenticated'
      ? { to: accountPathFor(session), label: 'Mi cuenta', icon: 'user' }
      : { to: '/ingresar', label: 'Ingresar', icon: 'user' },
  ];
}
