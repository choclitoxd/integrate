import { describe, expect, it } from 'vitest';

import { findPlaceByName, normalizePlaceName, otherMunicipalityMentioned } from './places';

const antioquia = [
  { code: '05001', name: 'MEDELLÍN' },
  { code: '05088', name: 'BELLO' },
  { code: '05045', name: 'APARTADÓ' },
];

describe('normalizePlaceName', () => {
  it('ignora tildes, mayúsculas y puntuación', () => {
    expect(normalizePlaceName('MEDELLÍN')).toBe('medellin');
    expect(normalizePlaceName('  Apartadó ')).toBe('apartado');
  });

  it('equipara Bogotá con BOGOTÁ, D.C.', () => {
    expect(normalizePlaceName('BOGOTÁ, D.C.')).toBe('bogota');
    expect(normalizePlaceName('Bogotá')).toBe('bogota');
    expect(normalizePlaceName('Bogotá Distrito Capital')).toBe('bogota');
  });
});

describe('findPlaceByName', () => {
  it('encuentra un único lugar por nombre normalizado', () => {
    expect(findPlaceByName(antioquia, 'Medellín')?.code).toBe('05001');
  });

  it('devuelve undefined si no hay coincidencia, está vacío o es ambiguo', () => {
    expect(findPlaceByName(antioquia, 'Rionegro')).toBeUndefined();
    expect(findPlaceByName(antioquia, undefined)).toBeUndefined();
    expect(findPlaceByName(antioquia, '')).toBeUndefined();
    const duplicated = [...antioquia, { code: '99999', name: 'Bello' }];
    expect(findPlaceByName(duplicated, 'Bello')).toBeUndefined();
  });
});

describe('otherMunicipalityMentioned', () => {
  const medellin = { code: '05001', name: 'MEDELLÍN' };

  it('avisa cuando la dirección nombra otro municipio del departamento', () => {
    expect(otherMunicipalityMentioned('Parque principal de Bello', medellin, antioquia)).toBe(
      'BELLO',
    );
  });

  it('no avisa si la dirección ya nombra el municipio detectado o ninguno', () => {
    expect(
      otherMunicipalityMentioned('Parque de Medellín y Bello', medellin, antioquia),
    ).toBeUndefined();
    expect(
      otherMunicipalityMentioned('Vereda El Plan, sendero norte', medellin, antioquia),
    ).toBeUndefined();
    expect(otherMunicipalityMentioned('', medellin, antioquia)).toBeUndefined();
  });
});
