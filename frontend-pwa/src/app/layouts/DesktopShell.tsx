import { Link, NavLink, Outlet } from 'react-router';

import { Icon, SkipLink } from '@/shared/ui';

import { MAIN_CONTENT_ID, useShellRoute, type ShellProps } from './shell';

/**
 * Shell de escritorio (ADR-0020 §4): navegación horizontal arriba y contenedor max-w-6xl. Las rutas
 * sin vista de escritorio (`wide`) se muestran en su columna móvil centrada; si traen barra de acción
 * fija, el pie le deja espacio.
 */
export function DesktopShell({ navLabel, navItems, homeTo, children }: ShellProps) {
  const { flow, wide, loading } = useShellRoute();
  const column = wide ? 'max-w-6xl px-8' : 'max-w-md px-4';
  const reserveActionBar = flow && !wide;

  return (
    <div className="flex min-h-dvh flex-col bg-canvas">
      <SkipLink targetId={MAIN_CONTENT_ID} />
      <header className="sticky top-0 z-20 bg-brand text-on-brand">
        <div className="mx-auto flex min-h-16 w-full max-w-6xl items-center justify-between gap-8 px-8">
          <Link to={homeTo} className="rounded-sm py-2 text-2xl font-bold tracking-tight">
            Intégrate
          </Link>
          <nav aria-label={navLabel}>
            <ul className="flex gap-2">
              {navItems.map((item) => (
                <li key={item.to}>
                  <NavLink
                    to={item.to}
                    end
                    className="inline-flex min-h-11 items-center gap-2 rounded-lg px-4 font-semibold text-on-brand hover:bg-brand-hover aria-[current=page]:bg-canvas aria-[current=page]:text-brand"
                  >
                    <Icon name={item.icon} size={20} />
                    {item.label}
                  </NavLink>
                </li>
              ))}
            </ul>
          </nav>
        </div>
        {loading && (
          <div role="status" className="absolute inset-x-0 bottom-0 h-1 bg-accent-soft">
            <span className="sr-only">Cargando…</span>
          </div>
        )}
      </header>

      <main
        id={MAIN_CONTENT_ID}
        tabIndex={-1}
        className={`mx-auto w-full flex-1 pt-6 ${column} ${reserveActionBar ? 'pb-36' : 'pb-12'}`}
      >
        {children ?? <Outlet />}
      </main>

      <footer className="border-t border-border-subtle bg-surface">
        <div
          className={`mx-auto w-full max-w-6xl px-8 pt-4 ${reserveActionBar ? 'pb-36' : 'pb-4'}`}
        >
          <p className="text-sm text-ink-muted">Intégrate · Turismo comunitario en Colombia</p>
        </div>
      </footer>
    </div>
  );
}
