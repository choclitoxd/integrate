import { hasRole, useSession } from '@/shared/session';

import { AppShell, type NavItem } from './AppShell';

/**
 * Navegación del turista: el último destino cambia con la sesión (ADR-0013 §6). «Gestor» solo
 * existe para quien tiene ROLE_LINK_MANAGER: a un visitante o a un turista le llevaría a un muro.
 */
export function TouristLayout() {
  const session = useSession();
  const navItems: readonly NavItem[] = [
    { to: '/', label: 'Explorar', icon: 'compass' },
    // Las reservas son del turista: a un visitante o a un gestor sin ese rol le llevaría a un muro.
    ...(hasRole(session, 'ROLE_TOURIST')
      ? [{ to: '/mis-experiencias', label: 'Mis experiencias', icon: 'ticket' } as const]
      : []),
    ...(hasRole(session, 'ROLE_LINK_MANAGER')
      ? [{ to: '/gestor', label: 'Gestor', icon: 'route' } as const]
      : []),
    session.status === 'authenticated'
      ? { to: '/cuenta', label: 'Mi cuenta', icon: 'user' }
      : { to: '/ingresar', label: 'Ingresar', icon: 'user' },
  ];
  return <AppShell navLabel="Navegación principal" navItems={navItems} homeTo="/" />;
}
