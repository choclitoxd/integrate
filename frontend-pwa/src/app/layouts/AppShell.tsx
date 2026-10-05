import type { ReactNode } from 'react';
import { NavLink, Outlet } from 'react-router';

import { SkipLink } from '@/shared/ui';

export const MAIN_CONTENT_ID = 'contenido';

export interface NavItem {
  readonly to: string;
  readonly label: string;
}

interface AppShellProps {
  /** Nombre accesible de la navegación principal. */
  readonly navLabel: string;
  readonly navItems: readonly NavItem[];
  /** Contenido de la ruta; por defecto el <Outlet /> del router. */
  readonly children?: ReactNode;
}

/** Estructura común: skip link, header con landmark de navegación y <main> enfocable. */
export function AppShell({ navLabel, navItems, children }: AppShellProps) {
  return (
    <div className="flex min-h-dvh flex-col">
      <SkipLink targetId={MAIN_CONTENT_ID} />
      <header className="bg-brand text-on-brand">
        <div className="mx-auto flex max-w-5xl flex-wrap items-center justify-between gap-4 px-4 py-3">
          <span className="text-xl font-bold">Intégrate</span>
          <nav aria-label={navLabel}>
            <ul className="flex flex-wrap gap-2">
              {navItems.map((item) => (
                <li key={item.to}>
                  <NavLink
                    to={item.to}
                    end
                    className="inline-flex min-h-11 items-center rounded px-3 font-semibold underline-offset-4 hover:underline aria-[current=page]:underline"
                  >
                    {item.label}
                  </NavLink>
                </li>
              ))}
            </ul>
          </nav>
        </div>
      </header>
      <main
        id={MAIN_CONTENT_ID}
        tabIndex={-1}
        className="mx-auto w-full max-w-5xl flex-1 px-4 py-8"
      >
        {children ?? <Outlet />}
      </main>
      <footer className="border-t border-border bg-surface">
        <p className="mx-auto max-w-5xl px-4 py-4 text-sm text-ink-muted">
          Intégrate · Turismo comunitario en Colombia
        </p>
      </footer>
    </div>
  );
}
