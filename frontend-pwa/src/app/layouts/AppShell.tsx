import type { ReactNode } from 'react';
import {
  Link,
  NavLink,
  Outlet,
  ScrollRestoration,
  useLocation,
  useMatches,
  useNavigate,
  useNavigation,
} from 'react-router';

import { Icon, SkipLink, type IconName } from '@/shared/ui';

export const MAIN_CONTENT_ID = 'contenido';

export interface NavItem {
  readonly to: string;
  readonly label: string;
  readonly icon: IconName;
}

/**
 * Metadatos de ruta para el shell (ADR-0012 §1). Una ruta `flow` tiene acción fija propia:
 * el shell oculta la navegación inferior y ofrece "Atrás" hacia `back` si no hay historial.
 */
export interface ShellHandle {
  readonly flow?: boolean;
  readonly back?: (params: Readonly<Record<string, string | undefined>>) => string;
}

function isShellHandle(handle: unknown): handle is ShellHandle {
  return typeof handle === 'object' && handle !== null;
}

interface AppShellProps {
  /** Nombre accesible de la navegación principal. */
  readonly navLabel: string;
  readonly navItems: readonly NavItem[];
  /** Destino de la marca en la barra superior. */
  readonly homeTo: string;
  /** Contenido de la ruta; por defecto el <Outlet /> del router. */
  readonly children?: ReactNode;
}

function BackButton({ fallback }: { fallback: string }) {
  const navigate = useNavigate();
  const location = useLocation();
  return (
    <button
      type="button"
      onClick={() => {
        // Con historial dentro de la app se vuelve atrás (conserva filtros y scroll).
        if (location.key !== 'default') void navigate(-1);
        else void navigate(fallback);
      }}
      className="-ml-2 inline-flex min-h-11 items-center gap-1 rounded-lg pr-3 pl-1 font-semibold hover:bg-brand-hover"
    >
      <Icon name="chevronLeft" size={24} />
      Atrás
    </button>
  );
}

/**
 * Shell móvil: columna max-w-md con barra superior, <main> enfocable, pie de créditos y
 * navegación inferior fija en la zona del pulgar (FE-DIR-001, Directiva 1).
 */
export function AppShell({ navLabel, navItems, homeTo, children }: AppShellProps) {
  const matches = useMatches();
  const leaf = matches[matches.length - 1];
  const handle = isShellHandle(leaf?.handle) ? leaf.handle : {};
  const flow = handle.flow === true;
  const backTo = handle.back?.(leaf?.params ?? {});
  // Una ruta diferida se descarga al abrirla por primera vez: la pausa se señala (ADR-0018 §1).
  const loading = useNavigation().state === 'loading';

  return (
    <div className="min-h-dvh bg-surface">
      <div className="mx-auto flex min-h-dvh w-full max-w-md flex-col bg-canvas md:border-x md:border-border-subtle">
        <SkipLink targetId={MAIN_CONTENT_ID} />
        <header className="sticky top-0 z-20 bg-brand pt-[env(safe-area-inset-top)] text-on-brand">
          <div className="flex min-h-14 items-center justify-between gap-3 px-4">
            {backTo ? (
              <BackButton fallback={backTo} />
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

        <main
          id={MAIN_CONTENT_ID}
          tabIndex={-1}
          className={`flex-1 px-4 pt-6 ${flow ? 'pb-36' : 'pb-8'}`}
        >
          {children ?? <Outlet />}
        </main>

        <footer className={`px-4 pt-2 ${flow ? 'pb-36' : 'pb-24'}`}>
          <p className="border-t border-border-subtle pt-4 text-sm text-ink-muted">
            Intégrate · Turismo comunitario en Colombia
          </p>
        </footer>

        {!flow && (
          <nav aria-label={navLabel} className="fixed inset-x-0 bottom-0 z-30">
            <ul className="mx-auto flex w-full max-w-md gap-2 border-t-2 border-border-strong bg-surface-elevated px-2 pt-1.5 pb-[max(0.375rem,env(safe-area-inset-bottom))]">
              {navItems.map((item) => (
                <li key={item.to} className="flex-1">
                  <NavLink
                    to={item.to}
                    end
                    className="flex min-h-14 flex-col items-center justify-center gap-0.5 rounded-lg px-2 text-sm font-semibold text-ink-muted hover:bg-surface-subtle aria-[current=page]:bg-brand-soft aria-[current=page]:text-brand"
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
      <ScrollRestoration />
    </div>
  );
}
