import { ScrollRestoration } from 'react-router';

import { AdminMobileNotice } from '@/features/admin';
import { useFormFactor } from '@/shared/lib';

import { AdminShell } from './AdminShell';
import { MobileShell } from './MobileShell';
import type { NavItem } from './shell';

const MOBILE_NAV: readonly NavItem[] = [
  { to: '/', label: 'Explorar', icon: 'compass' },
  { to: '/admin', label: 'Admin', icon: 'shield' },
  { to: '/cuenta', label: 'Mi cuenta', icon: 'user' },
];

/**
 * Consola de administración (ADR-0042); la ruta exige ROLE_ADMIN y todo este módulo viaja en un
 * chunk diferido. En escritorio monta `AdminShell`; en celular o tablet, cualquier ruta de `/admin`
 * muestra el aviso con el resumen en solo lectura (§4).
 */
export function AdminLayout() {
  const formFactor = useFormFactor();
  return (
    <>
      {formFactor === 'desktop' ? (
        <AdminShell />
      ) : (
        <MobileShell navLabel="Navegación principal" navItems={MOBILE_NAV} homeTo="/">
          <AdminMobileNotice />
        </MobileShell>
      )}
      <ScrollRestoration />
    </>
  );
}
