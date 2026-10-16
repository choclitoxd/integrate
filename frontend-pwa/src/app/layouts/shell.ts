import type { ReactNode } from 'react';
import { useMatches, useNavigation } from 'react-router';

import type { IconName } from '@/shared/ui';

export const MAIN_CONTENT_ID = 'contenido';

export interface NavItem {
  readonly to: string;
  readonly label: string;
  readonly icon: IconName;
}

/**
 * Metadatos de ruta para el shell (ADR-0012 §1). Una ruta `flow` tiene acción fija propia: el shell
 * móvil oculta la navegación inferior y ofrece "Atrás" hacia `back` si no hay historial. Una ruta
 * `wide` tiene vista de escritorio dedicada (ADR-0020 §4): el shell de escritorio no la estrecha.
 */
export interface ShellHandle {
  readonly flow?: boolean;
  readonly wide?: boolean;
  readonly back?: (params: Readonly<Record<string, string | undefined>>) => string;
}

export interface ShellProps {
  /** Nombre accesible de la navegación principal. */
  readonly navLabel: string;
  readonly navItems: readonly NavItem[];
  /** Destino de la marca en la barra superior. */
  readonly homeTo: string;
  /** Contenido de la ruta; por defecto el <Outlet /> del router. */
  readonly children?: ReactNode;
}

function isShellHandle(handle: unknown): handle is ShellHandle {
  return typeof handle === 'object' && handle !== null;
}

/** Lo que ambos shells leen de la ruta activa. */
export function useShellRoute() {
  const matches = useMatches();
  const leaf = matches[matches.length - 1];
  const handle = isShellHandle(leaf?.handle) ? leaf.handle : {};
  // Una ruta diferida se descarga al abrirla por primera vez: la pausa se señala (ADR-0018 §1).
  const loading = useNavigation().state === 'loading';
  return {
    flow: handle.flow === true,
    wide: handle.wide === true,
    backTo: handle.back?.(leaf?.params ?? {}),
    loading,
  };
}
