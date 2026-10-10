import { AppShell, type NavItem } from './AppShell';

const NAV_ITEMS: readonly NavItem[] = [
  { to: '/gestor', label: 'Jornada', icon: 'route' },
  { to: '/gestor/anfitriones', label: 'Anfitriones', icon: 'users' },
  { to: '/cuenta', label: 'Mi cuenta', icon: 'user' },
];

/** Shell del Gestor de Enlace; la ruta exige ROLE_LINK_MANAGER (ADR-0013 §6). */
export function ManagerLayout() {
  return <AppShell navLabel="Navegación del gestor" navItems={NAV_ITEMS} homeTo="/gestor" />;
}
