import { describe, expect, it } from 'vitest';

import { parseCoordinate, parseGoogleMapsLink, roundCoordinate } from './googleMaps';

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

describe('parseGoogleMapsLink', () => {
  it('lee el patrón @lat,lng de un enlace largo', () => {
    expect(parseGoogleMapsLink('https://www.google.com/maps/@4.6097,-74.0817,17z')).toEqual({
      kind: 'point',
      point: { lat: 4.6097, lng: -74.0817 },
    });
  });

  it('lee el parámetro q con coordenadas', () => {
    expect(parseGoogleMapsLink('https://www.google.com/maps?q=6.2442,-75.5812')).toEqual({
      kind: 'point',
      point: { lat: 6.2442, lng: -75.5812 },
    });
  });

  it('lee el patrón !3d/!4d de un lugar', () => {
    expect(
      parseGoogleMapsLink('https://www.google.com/maps/place/Vereda/data=!3m1!4b1!3d4.7!4d-74.2'),
    ).toEqual({ kind: 'point', point: { lat: 4.7, lng: -74.2 } });
  });

  it('rechaza enlaces cortos y pide el largo', () => {
    expect(parseGoogleMapsLink('https://maps.app.goo.gl/AbCdEf')).toEqual({ kind: 'short' });
  });

  it('rechaza dominios que solo se parecen a Google', () => {
    expect(parseGoogleMapsLink('https://evilgoogle.com/maps/@4.6,-74.1')).toEqual({
      kind: 'invalid',
    });
  });

  it('rechaza puntos fuera de Colombia y texto sin enlace', () => {
    expect(parseGoogleMapsLink('https://www.google.com/maps/@40.7,-74.0')).toEqual({
      kind: 'invalid',
    });
    expect(parseGoogleMapsLink('Parque Nacional')).toEqual({ kind: 'invalid' });
  });
});
