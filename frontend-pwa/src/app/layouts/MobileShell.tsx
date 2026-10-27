import { Link, NavLink, Outlet } from 'react-router';

import { Icon, SkipLink } from '@/shared/ui';

import { MAIN_CONTENT_ID, useShellRoute, type ShellProps } from './shell';
import { BackButton } from './ShellParts';

/**
 * Shell móvil: columna max-w-md con barra superior, <main> enfocable, pie de créditos y
 * navegación inferior fija en la zona del pulgar (FE-DIR-001, Directiva 1). Las acciones de cada
 * vista van dentro del contenido, no en una barra fija: la única barra fija es esta navegación.
 */
export function MobileShell({ navLabel, navItems, homeTo, children }: ShellProps) {
  const { flow, backTo, loading } = useShellRoute();

  return (
    <div className="min-h-dvh bg-surface">
      <div className="mx-auto flex min-h-dvh w-full max-w-md flex-col bg-canvas md:border-x md:border-border-subtle">
        <SkipLink targetId={MAIN_CONTENT_ID} />
        <header className="sticky top-0 z-20 bg-brand pt-[env(safe-area-inset-top)] text-on-brand">
          <div className="flex min-h-14 items-center justify-between gap-3 px-4">
            {backTo ? (
              <BackButton fallback={backTo} className="-ml-2 hover:bg-brand-hover" />
            ) : (
              <Link to={homeTo} className="rounded-sm py-2 text-xl font-bold tracking-tight">
                Intégrate
              </Link>
            )}
            {backTo && (
              <span aria-hidden="true" className="text-lg font-bold tracking-tight">
                Intégrate
              </span>
            )}
          </div>
          {loading && (
            <div role="status" className="absolute inset-x-0 bottom-0 h-1 bg-accent-soft">
              <span className="sr-only">Cargando…</span>
            </div>
          )}
        </header>

        <main id={MAIN_CONTENT_ID} tabIndex={-1} className="flex-1 px-4 pt-6 pb-8">
          {children ?? <Outlet />}
        </main>

        {/* Con la navegación inferior (no en los flujos) el pie deja espacio para no quedar tapado. */}
        <footer className={`px-4 pt-2 ${flow ? 'pb-8' : 'pb-24'}`}>
          <div className="flex flex-col gap-1 border-t border-border-subtle pt-4 sm:flex-row sm:items-center sm:justify-between">
            <p className="text-sm text-ink-muted">Intégrate · Turismo comunitario en Colombia</p>
            <a
              href="/manual/index.html"
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex min-h-11 items-center font-semibold text-brand underline underline-offset-4 hover:text-brand-hover focus-visible:outline-3 focus-visible:outline-focus"
            >
              Manual de usuario ↗<span className="sr-only"> (se abre en una nueva pestaña)</span>
            </a>
          </div>
        </footer>

        {!flow && (
          <nav aria-label={navLabel} className="fixed inset-x-0 bottom-0 z-30">
            <ul className="mx-auto flex w-full max-w-md gap-2 border-t-2 border-border-strong bg-surface-elevated px-2 pt-1.5 pb-[max(0.375rem,env(safe-area-inset-bottom))]">
              {navItems.map((item) => (
                <li key={item.to} className="flex-auto">
                  <NavLink
                    to={item.to}
                    end
                    className="flex min-h-14 flex-col items-center justify-center gap-0.5 rounded-lg px-1 text-center text-sm font-semibold text-ink-muted hover:bg-surface-subtle aria-[current=page]:bg-brand-soft aria-[current=page]:text-brand"
                  >
                    <Icon name={item.icon} size={22} />
                    {item.label}
                  </NavLink>
                </li>
              ))}
            </ul>
          </nav>
        )}
      </div>
    </div>
  );
}
