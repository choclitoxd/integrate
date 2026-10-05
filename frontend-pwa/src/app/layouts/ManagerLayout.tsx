import { AppShell, type NavItem } from './AppShell';

const NAV_ITEMS: readonly NavItem[] = [
  { to: '/gestor', label: 'Panel de jornada' },
  { to: '/', label: 'Portal turista' },
];

/** Shell del Gestor de Enlace; la protección por rol (RBAC) se añade con la autenticación (T-1.2). */
export function ManagerLayout() {
  return <AppShell navLabel="Navegación del gestor" navItems={NAV_ITEMS} />;
}
