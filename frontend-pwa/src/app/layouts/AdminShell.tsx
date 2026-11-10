import { useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router';

import { useSession, useSessionStore } from '@/shared/session';
import { Icon, SkipLink } from '@/shared/ui';

import { MAIN_CONTENT_ID, useShellRoute, type NavItem } from './shell';

const NAV_ITEMS: readonly NavItem[] = [
  { to: '/admin', label: 'Resumen', icon: 'compass' },
  { to: '/admin/experiencias', label: 'Experiencias', icon: 'mountain' },
  { to: '/admin/asociaciones', label: 'Asociaciones', icon: 'users' },
  { to: '/admin/auditoria', label: 'Auditoría', icon: 'shield' },
];

const SIDE_LINK =
  'flex min-h-11 w-full items-center gap-3 rounded-lg px-3 text-left font-semibold text-ink-inverse hover:bg-border-strong';

/**
 * Shell de la consola de administración (ADR-0042 §1): barra lateral fija en superficie invertida y
 * área de trabajo a ancho completo. Es distinto a propósito de los shells del turista y del gestor,
 * para que el contexto administrativo sea inconfundible. Solo se monta en escritorio.
 */
export function AdminShell() {
  const { loading } = useShellRoute();
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const session = useSession();
  const store = useSessionStore();
  const [signOutError, setSignOutError] = useState(false);
  const user = session.status === 'authenticated' ? session.user : null;
  const section =
    [...NAV_ITEMS]
      .reverse()
      .find((item) => pathname === item.to || pathname.startsWith(`${item.to}/`))?.label ??
    'Resumen';

  return (
    <div className="flex min-h-dvh bg-surface">
      <SkipLink targetId={MAIN_CONTENT_ID} />
      <header className="sticky top-0 flex h-dvh w-64 shrink-0 flex-col gap-6 overflow-y-auto bg-surface-inverse px-4 py-6 text-ink-inverse">
        <Link to="/admin" className="rounded-sm px-3 py-1">
          <span className="block text-2xl font-bold tracking-tight">Intégrate</span>
          <span className="block text-sm font-semibold tracking-wide uppercase">
            Administración
          </span>
        </Link>

        <nav aria-label="Navegación de administración" className="flex-1">
          <ul className="grid gap-2">
            {NAV_ITEMS.map((item) => (
              <li key={item.to}>
                <NavLink
                  to={item.to}
                  end={item.to === '/admin'}
                  className={`${SIDE_LINK} aria-[current=page]:bg-canvas aria-[current=page]:text-ink aria-[current=page]:hover:bg-canvas`}
                >
                  <Icon name={item.icon} size={20} />
                  {item.label}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>

        <div className="grid gap-2 border-t border-border-subtle pt-4">
          {user && (
            <p className="px-3 text-sm">
              <span className="block font-bold">{user.fullName}</span>
              <span className="block break-all">{user.email}</span>
            </p>
          )}
          <Link to="/" className={SIDE_LINK}>
            <Icon name="chevronLeft" size={20} />
            Volver al sitio
          </Link>
          <button
            type="button"
            className={SIDE_LINK}
            onClick={() => {
              setSignOutError(false);
              store
                .signOut()
                .then(() => navigate('/'))
                .catch(() => {
                  setSignOutError(true);
                });
            }}
          >
            <Icon name="user" size={20} />
            Cerrar sesión
          </button>
          {signOutError && (
            <p role="alert" className="px-3 text-sm font-semibold">
              No fue posible cerrar la sesión. Inténtalo de nuevo.
            </p>
          )}
        </div>
      </header>

      <div className="flex min-w-0 flex-1 flex-col">
        <div className="relative border-b-2 border-border-subtle bg-canvas px-8">
          <nav aria-label="Ruta de navegación" className="flex min-h-14 items-center">
            <ol className="flex items-center gap-2 font-semibold text-ink-muted">
              <li>Administración</li>
              <li aria-hidden="true">/</li>
              <li aria-current="page" className="text-ink">
                {section}
              </li>
            </ol>
          </nav>
          {loading && (
            <div role="status" className="absolute inset-x-0 bottom-0 h-1 bg-brand">
              <span className="sr-only">Cargando…</span>
            </div>
          )}
        </div>

        <main id={MAIN_CONTENT_ID} tabIndex={-1} className="flex-1 px-8 pt-8 pb-12">
          <Outlet />
        </main>

        <footer className="border-t border-border-subtle px-8 py-4">
          <p className="text-sm text-ink-muted">
            Intégrate · Consola de administración. Lo que hagas aquí queda en la bitácora de
            auditoría.
          </p>
        </footer>
      </div>
    </div>
  );
}
