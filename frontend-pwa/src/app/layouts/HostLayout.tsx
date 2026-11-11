import { AppShell, type NavItem } from './AppShell';

const NAV_ITEMS: readonly NavItem[] = [
  { to: '/anfitrion', label: 'Reservas', icon: 'ticket' },
  { to: '/cuenta', label: 'Mi cuenta', icon: 'user' },
];

export function HostLayout() {
  return <AppShell navLabel="Navegación del anfitrión" navItems={NAV_ITEMS} homeTo="/anfitrion" />;
}
