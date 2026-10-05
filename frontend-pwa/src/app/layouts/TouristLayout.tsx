import { AppShell, type NavItem } from './AppShell';

const NAV_ITEMS: readonly NavItem[] = [
  { to: '/', label: 'Inicio' },
  { to: '/gestor', label: 'Soy Gestor de Enlace' },
];

export function TouristLayout() {
  return <AppShell navLabel="Navegación principal" navItems={NAV_ITEMS} />;
}
