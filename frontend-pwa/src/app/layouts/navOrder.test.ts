import { describe, expect, it } from 'vitest';

import { accountLast } from './navOrder';
import { ADMIN_NAV_ITEMS, type NavItem } from './shell';

const item = (to: string): NavItem => ({ to, label: to, icon: 'user' });

describe('accountLast', () => {
  it('deja «Mi cuenta» al final aunque el layout agregue entradas después', () => {
    const host = [
      item('/'),
      item('/anfitrion'),
      item('/anfitrion/cuenta'),
      item('/anfitrion/metricas'),
      item('/anfitrion/experiencias'),
    ];
    expect(accountLast(host).map((entry) => entry.to)).toEqual([
      '/',
      '/anfitrion',
      '/anfitrion/metricas',
      '/anfitrion/experiencias',
      '/anfitrion/cuenta',
    ]);
  });
  it('trata «Ingresar» como la entrada de cuenta de una sesión anónima', () => {
    const anonymous = [item('/ingresar'), item('/')];
    expect(accountLast(anonymous).map((entry) => entry.to)).toEqual(['/', '/ingresar']);
  });
  it('devuelve la misma lista si ya está en orden, para no romper la identidad de la lista de admin', () => {
    expect(accountLast(ADMIN_NAV_ITEMS)).toBe(ADMIN_NAV_ITEMS);
  });
});
