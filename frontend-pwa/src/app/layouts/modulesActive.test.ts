import { describe, expect, it } from 'vitest';

import { isInsideModules } from './modulesActive';
import type { NavItem } from './shell';

const hostModules: readonly NavItem[] = [
  { to: '/anfitrion', label: 'Anfitrión', icon: 'calendar', group: 'Anfitrión' },
  { to: '/anfitrion/metricas', label: 'Mis métricas', icon: 'chart', group: 'Anfitrión' },
  {
    to: '/anfitrion/experiencias',
    label: 'Experiencias del anfitrión',
    icon: 'mountain',
    group: 'Anfitrión',
  },
];

describe('isInsideModules (ADR-0067 §3)', () => {
  it.each([
    '/anfitrion',
    '/anfitrion/metricas',
    '/anfitrion/experiencias',
    '/anfitrion/experiencias/42',
  ])('%s está dentro de los módulos del anfitrión', (pathname) => {
    expect(isInsideModules(pathname, hostModules)).toBe(true);
  });

  it.each(['/', '/mis-experiencias', '/gestor', '/anfitrionx'])(
    '%s no está dentro de los módulos del anfitrión',
    (pathname) => {
      expect(isInsideModules(pathname, hostModules)).toBe(false);
    },
  );

  it('«Mi cuenta» cuelga de la misma raíz pero no enciende «Módulos»', () => {
    expect(isInsideModules('/anfitrion/cuenta', hostModules)).toBe(false);
  });
});
