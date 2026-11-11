import { useLocation } from 'react-router';

import { isAccountItem } from './navOrder';
import type { NavItem } from './shell';

/**
 * ¿Está la ruta actual dentro de alguno de los módulos? «Mi cuenta» cuelga de la misma raíz
 * (`/anfitrion/cuenta`) pero no es un módulo: no enciende «Módulos» (ADR-0067 §3).
 */
export function isInsideModules(pathname: string, items: readonly NavItem[]): boolean {
  if (isAccountItem({ to: pathname, label: '', icon: 'user' })) return false;
  return items.some((item) => pathname === item.to || pathname.startsWith(`${item.to}/`));
}

/** Estado activo de «Módulos» para un rol de zona (anfitrión o gestor). */
export function useModulesActive(items: readonly NavItem[]): boolean {
  return isInsideModules(useLocation().pathname, items);
}
