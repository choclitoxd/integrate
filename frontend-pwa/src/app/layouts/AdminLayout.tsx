import { AppShell } from './AppShell';
import { ADMIN_NAV_ITEMS } from './shell';

/**
 * Panel de administración (ADR-0042, ADR-0044 §1): reutiliza `AppShell` (`DesktopShell` en computador
 * y `MobileShell` en celular) con la identidad visual verde de la marca y navegación exclusiva del rol,
 * sin los apartados de turista («Explorar», «Mis experiencias»).
 */
export function AdminLayout() {
  return (
    <AppShell navLabel="Navegación de administración" navItems={ADMIN_NAV_ITEMS} homeTo="/admin" />
  );
}
