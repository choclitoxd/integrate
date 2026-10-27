import { describe, expect, it } from 'vitest';

import {
  countryByDial,
  formatPhoneDisplay,
  joinPhone,
  lengthText,
  nationalDigits,
  PHONE_COUNTRIES,
  phoneProblem,
  splitPhone,
} from './phone';

const colombia = countryByDial('57');
const ecuador = countryByDial('593');
if (!colombia || !ecuador) throw new Error('Faltan países de prueba');

describe('celulares con indicativo', () => {
  it('ningún indicativo es prefijo de otro: un número completo se separa sin ambigüedad', () => {
    for (const a of PHONE_COUNTRIES) {
      for (const b of PHONE_COUNTRIES) {
        if (a !== b) expect(b.dial.startsWith(a.dial)).toBe(false);
      }
    }
    // Y el rango de cada país cabe en el E.164 del backend (8 a 14 dígitos tras el "+1 dígito").
    for (const country of PHONE_COUNTRIES) {
      expect(country.min).toBeLessThanOrEqual(country.max);
      expect(country.dial.length + country.min).toBeGreaterThanOrEqual(8);
      expect(country.dial.length + country.max).toBeLessThanOrEqual(15);
    }
  });

  it('recorta el número nacional al máximo del país y descarta lo que no es dígito', () => {
    expect(nationalDigits('300 123-4567 89', colombia)).toBe('3001234567');
    expect(nationalDigits('abc', colombia)).toBe('');
    expect(nationalDigits('99123456789', ecuador)).toBe('991234567');
  });

  it('separa el indicativo y el número en cualquiera de las formas habituales', () => {
    expect(splitPhone('+57 3001234567')).toEqual({ country: colombia, national: '3001234567' });
    expect(splitPhone('+573001234567')).toEqual({ country: colombia, national: '3001234567' });
    expect(splitPhone('(+593) 99-123-4567')).toEqual({ country: ecuador, national: '991234567' });
    // El valor inicial de los formularios y un campo vacío.
    expect(splitPhone('+57 ')).toEqual({ country: colombia, national: '' });
    expect(splitPhone('')).toEqual({ country: colombia, national: '' });
    // Sin indicativo reconocible se conserva el país de respaldo.
    expect(splitPhone('300 123 4567', ecuador)).toEqual({
      country: ecuador,
      national: '300123456',
    });
  });

  it('une indicativo y número en un solo valor que normalizePhone y el backend entienden', () => {
    expect(joinPhone(colombia, '3001234567')).toBe('+57 3001234567');
    expect(joinPhone(colombia, '')).toBe('+57 ');
  });

  it('exige el largo del país y dice cuánto es', () => {
    expect(phoneProblem('+573001234567')).toBeNull();
    expect(phoneProblem('+57300123456')).toBe(
      'Escribe un celular de 10 dígitos para Colombia (+57).',
    );
    expect(phoneProblem('+5730012345678')).toMatch(/10 dígitos para Colombia/);
    expect(phoneProblem('+57')).toMatch(/10 dígitos para Colombia/);
    expect(phoneProblem('+593991234567')).toBeNull();
    expect(phoneProblem('+5939912345678')).toMatch(/9 dígitos para Ecuador \(\+593\)/);
    // Rangos: Brasil acepta 10 u 11 dígitos.
    expect(phoneProblem('+5511987654321')).toBeNull();
    expect(phoneProblem('+551187654321')).toBeNull();
    expect(phoneProblem('+55119876543')).toMatch(/10 u 11 dígitos para Brasil/);
  });

  it('un indicativo fuera de la lista solo debe cumplir el E.164 del backend', () => {
    expect(phoneProblem('+8613800138000')).toBeNull();
    expect(phoneProblem('+86138')).toMatch(/celular válido/);
    expect(phoneProblem('123')).toMatch(/celular válido/);
    expect(phoneProblem('')).toMatch(/celular válido/);
  });

  it('describe el largo en texto y da formato de lectura a los números', () => {
    expect(lengthText(colombia)).toBe('10 dígitos');
    expect(lengthText(countryByDial('55') ?? colombia)).toBe('10 u 11 dígitos');
    expect(formatPhoneDisplay('+573001234567')).toBe('+57 300 123 4567');
    expect(formatPhoneDisplay('+593991234567')).toBe('+593 991234567');
    expect(formatPhoneDisplay('+8613800138000')).toBe('+8613800138000');
  });
});
