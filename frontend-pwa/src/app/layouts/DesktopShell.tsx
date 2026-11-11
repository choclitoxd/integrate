import { Fragment } from 'react';
import { Link, NavLink, Outlet } from 'react-router';

import { Icon, SkipLink } from '@/shared/ui';

import { ADMIN_NAV_ITEMS, MAIN_CONTENT_ID, useShellRoute, type ShellProps } from './shell';
import { AdminModules } from './AdminModules';
import { useModulesActive } from './modulesActive';
import { isAccountItem } from './navOrder';
import { RoleIndicator } from './RoleIndicator';

/**
 * Shell de escritorio (ADR-0020 §4): navegación horizontal arriba y contenedor max-w-6xl. Las rutas
 * sin vista de escritorio (`wide`) se muestran en su columna móvil centrada. Las acciones van dentro
 * del contenido (o en el panel lateral de las vistas dedicadas), nunca en una barra fija al fondo.
 */
export function DesktopShell({ navLabel, navItems, homeTo, children }: ShellProps) {
  const { wide, centerOnDesktop, loading } = useShellRoute();
  const admin = navItems === ADMIN_NAV_ITEMS;
  // Con ambos roles de zona la barra no cabe por debajo de `xl`: se agrupa bajo «Módulos» (ADR-0067).
  const roleModules = admin ? [] : navItems.filter((item) => item.group);
  const collapse = new Set(roleModules.map((item) => item.group)).size > 1;
  const modulesActive = useModulesActive(roleModules);
  const column = wide ? 'max-w-6xl px-8' : 'max-w-md px-4';
  const verticalLayout = centerOnDesktop ? 'grid content-center py-6' : 'pt-6 pb-12';

  return (
    <div className="flex min-h-dvh flex-col bg-canvas">
      <SkipLink targetId={MAIN_CONTENT_ID} />
      <header className="sticky top-0 z-20 bg-brand text-on-brand">
        <div
          className={`mx-auto flex min-h-16 w-full items-center justify-between gap-6 px-6 xl:px-8 ${admin ? 'max-w-7xl' : 'max-w-6xl'}`}
        >
          <div className="shrink-0 py-2">
            <Link to={homeTo} className="rounded-sm text-2xl font-bold tracking-tight">
              Intégrate
            </Link>
            <RoleIndicator />
          </div>
          <nav aria-label={navLabel} className={admin ? 'flex-1' : undefined}>
            <ul className={`flex items-center ${admin ? 'justify-end gap-1.5' : 'gap-2'}`}>
              {navItems.map((item) => {
                const isAdminHome = admin && item.to === '/admin';
                const isAdminAccount = admin && item.to === '/admin/cuenta';
                const isAdminModule = admin && !isAdminHome && !isAdminAccount;
                const isRoleModule = collapse && !!item.group;
                return (
                  <Fragment key={item.to}>
                    {collapse && isAccountItem(item) && (
                      <li className="xl:hidden">
                        <AdminModules
                          items={roleModules}
                          title="Módulos"
                          icon="grid"
                          active={modulesActive}
                        />
                      </li>
                    )}
                    {isAdminAccount && (
                      <li className="xl:hidden">
                        <AdminModules items={navItems.slice(1, -1)} />
                      </li>
                    )}
                    <li
                      className={
                        isAdminModule || isRoleModule
                          ? 'hidden xl:block'
                          : isAdminHome
                            ? 'xl:ml-auto'
                            : isAdminAccount
                              ? 'ml-1 border-l border-brand-border pl-3 xl:ml-auto xl:pl-4'
                              : undefined
                      }
                    >
                      <NavLink
                        to={item.to}
                        end
                        className={`inline-flex min-h-11 min-w-11 shrink-0 items-center whitespace-nowrap rounded-lg font-semibold text-on-brand hover:bg-brand-hover aria-[current=page]:bg-canvas aria-[current=page]:text-brand ${admin ? 'gap-1.5 px-3 text-sm' : 'gap-2 px-4'}`}
                      >
                        <Icon name={item.icon} size={admin ? 16 : 20} />
                        {item.label}
                      </NavLink>
                    </li>
                  </Fragment>
                );
              })}
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
        className={`mx-auto w-full flex-1 ${verticalLayout} ${column}`}
      >
        {children ?? <Outlet />}
      </main>

      <footer className="border-t border-border-subtle bg-surface">
        <div className="mx-auto flex w-full max-w-6xl items-center justify-between px-8 pt-4 pb-4">
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
    </div>
  );
}
