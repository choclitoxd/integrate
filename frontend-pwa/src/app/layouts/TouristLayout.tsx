import { Navigate, useLocation } from 'react-router';

import { hasRole, useSession } from '@/shared/session';

import { AppShell, type NavItem } from './AppShell';
import { ADMIN_NAV_ITEMS } from './shell';

const TOURIST_ONLY_PREFIXES = ['/experiencias', '/mis-experiencias', '/reservas'] as const;

/**
 * Navegación pública y del turista (ADR-0013 §6, ADR-0044 §2):
 * - Si la sesión pertenece a un Administrador (`ROLE_ADMIN`), nunca ve «Explorar» ni «Mis experiencias»:
 *   en `/` o rutas de compra/catálogo se redirige a `/admin`, y `/cuenta` se redirige a `/admin/cuenta`.
 * - «Gestor» solo aparece para quien tiene `ROLE_LINK_MANAGER`.
 */
export function TouristLayout() {
  const session = useSession();
  const { pathname } = useLocation();

  if (hasRole(session, 'ROLE_ADMIN')) {
    // Su perfil vive en el panel, no en la cuenta de turista: /cuenta le lleva a /admin/cuenta.
    if (pathname === '/cuenta') {
      return <Navigate to="/admin/cuenta" replace />;
    }
    if (
      pathname === '/' ||
      TOURIST_ONLY_PREFIXES.some(
        (prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`),
      )
    ) {
      return <Navigate to="/admin" replace />;
    }
    return (
      <AppShell
        navLabel="Navegación de administración"
        navItems={ADMIN_NAV_ITEMS}
        homeTo="/admin"
      />
    );
  }

  const navItems: readonly NavItem[] = [
    { to: '/', label: 'Explorar', icon: 'compass' },
    // Las reservas son del turista: a un visitante o a un gestor sin ese rol le llevaría a un muro.
    ...(hasRole(session, 'ROLE_TOURIST')
      ? [{ to: '/mis-experiencias', label: 'Mis experiencias', icon: 'ticket' } as const]
      : []),
    ...(hasRole(session, 'ROLE_HOST')
      ? [{ to: '/anfitrion', label: 'Anfitrión', icon: 'ticket' } as const]
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
