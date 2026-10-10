import { useSession } from '@/shared/session';

import { AppShell, type NavItem } from './AppShell';

/** Navegación del turista: el tercer destino cambia con la sesión (ADR-0013 §6). */
export function TouristLayout() {
  const session = useSession();
  const navItems: readonly NavItem[] = [
    { to: '/', label: 'Explorar', icon: 'compass' },
    { to: '/gestor', label: 'Gestor', icon: 'route' },
    session.status === 'authenticated'
      ? { to: '/cuenta', label: 'Mi cuenta', icon: 'user' }
      : { to: '/ingresar', label: 'Ingresar', icon: 'user' },
  ];
  return <AppShell navLabel="Navegación principal" navItems={navItems} homeTo="/" />;
}
