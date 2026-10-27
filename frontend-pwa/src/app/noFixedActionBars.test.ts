import { describe, expect, it } from 'vitest';

/**
 * Las acciones principales (Pagar, Reservar, Crear cuenta, Mostrar QR…) van dentro del contenido,
 * justo después de lo que confirman, y no en una barra fija al fondo de la pantalla, que en celular
 * queda lejos de los datos y en computador flota bajo el pie de página (FE-DIR-001, Directiva 1.4).
 * Esta prueba lee el código real y falla si alguien reintroduce el patrón.
 */

const sources = import.meta.glob<string>(
  ['/src/**/*.tsx', '!/src/**/*.test.tsx', '!/src/test/**'],
  { query: '?raw', import: 'default', eager: true },
);

/** Lo único que sí puede ir fijo abajo: la navegación del shell móvil y las hojas/diálogos modales. */
const ALLOWED_FIXED_BOTTOM = new Set([
  '/src/app/layouts/MobileShell.tsx',
  '/src/shared/ui/BottomSheet.tsx',
]);

describe('acciones sin barras fijas al fondo', () => {
  it('ningún componente usa un StickyActionBar', () => {
    const offenders = Object.entries(sources)
      .filter(([, code]) => code.includes('StickyActionBar'))
      .map(([path]) => path);
    expect(offenders).toEqual([]);
  });

  it('solo la navegación del shell y las hojas modales se fijan al borde inferior', () => {
    expect(Object.keys(sources).length).toBeGreaterThan(20);
    const offenders = Object.entries(sources)
      .filter(([path]) => !ALLOWED_FIXED_BOTTOM.has(path))
      // `fixed` junto a `bottom-0` en la misma lista de clases de un elemento.
      .filter(([, code]) => /className=[^\n]*\bfixed\b[^\n]*\bbottom-0\b/.test(code))
      .map(([path]) => path);
    expect(offenders).toEqual([]);
  });
});
