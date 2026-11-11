import type { NavItem } from './shell';

/** «Mi cuenta» (`/cuenta`, `/anfitrion/cuenta`, `/admin/cuenta`…) o «Ingresar» cuando no hay sesión. */
export function isAccountItem(item: NavItem): boolean {
  return item.to === '/ingresar' || item.to === '/cuenta' || item.to.endsWith('/cuenta');
}

/**
 * La entrada de cuenta va siempre a la derecha de todo, sea cual sea el rol y cuantas entradas
 * agregue un layout. Si ya está al final devuelve la misma lista (los shells comparan por identidad
 * la lista de administración).
 */
export function accountLast(items: readonly NavItem[]): readonly NavItem[] {
  const others = items.filter((item) => !isAccountItem(item));
  const account = items.filter(isAccountItem);
  const ordered = [...others, ...account];
  return ordered.every((item, index) => item === items[index]) ? items : ordered;
}
