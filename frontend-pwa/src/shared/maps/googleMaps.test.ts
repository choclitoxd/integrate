import { describe, expect, it } from 'vitest';

import { parseCoordinate, roundCoordinate } from './googleMaps';

describe('parseCoordinate', () => {
  it('acepta punto o coma decimal y recorta espacios', () => {
    expect(parseCoordinate(' 4.570868 ')).toBe(4.570868);
    expect(parseCoordinate('-74,297333')).toBe(-74.297333);
  });

  it('devuelve null para vacío o texto no numérico', () => {
    expect(parseCoordinate('')).toBeNull();
    expect(parseCoordinate('   ')).toBeNull();
    expect(parseCoordinate('abc')).toBeNull();
  });
});

describe('roundCoordinate', () => {
  it('redondea a 6 decimales', () => {
    expect(roundCoordinate(4.5708681234)).toBe(4.570868);
  });
});
