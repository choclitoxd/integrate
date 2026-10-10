import { describe, expect, it } from 'vitest';

import {
  addDays,
  formatCop,
  formatDateTime,
  formatDuration,
  formatLocalTime,
  formatLongDate,
  startOfWeek,
  todayInBogota,
  toTitleCase,
  weekdayInitial,
} from './format';

// Intl separa con espacios duros; se normalizan para comparar.
const plain = (text: string) => text.replace(/\s/g, ' ');

describe('format', () => {
  it('formatea pesos colombianos sin centavos', () => {
    expect(plain(formatCop(80000))).toBe('$ 80.000');
    expect(plain(formatCop(1250000))).toBe('$ 1.250.000');
  });

  it('expresa la duración en minutos, horas o días', () => {
    expect(formatDuration(45)).toBe('45 min');
    expect(formatDuration(240)).toBe('4 h');
    expect(formatDuration(270)).toBe('4 h 30 min');
    expect(formatDuration(1440)).toBe('1 día');
    expect(formatDuration(3000)).toBe('2 días 2 h');
  });

  it('convierte nombres DANE a formato título respetando conectores', () => {
    expect(toTitleCase('SAN JUAN DE NEPOMUCENO')).toBe('San Juan de Nepomuceno');
    expect(toTitleCase('BOGOTÁ, D.C.')).toBe('Bogotá, D.C.');
    expect(toTitleCase('EL CARMEN DE BOLÍVAR')).toBe('El Carmen de Bolívar');
  });

  it('calcula "hoy" en Bogotá aunque en UTC ya sea el día siguiente', () => {
    // 2026-10-16 02:00 UTC = 2026-10-15 21:00 en Bogotá (UTC-5).
    expect(todayInBogota(new Date('2026-10-16T02:00:00Z'))).toBe('2026-10-15');
  });

  it('opera con fechas civiles sin desplazamientos de zona', () => {
    expect(addDays('2026-10-31', 1)).toBe('2026-11-01');
    expect(addDays('2026-10-15', 59)).toBe('2026-12-13');
    expect(startOfWeek('2026-10-15')).toBe('2026-10-12'); // jueves → lunes
    expect(startOfWeek('2026-10-18')).toBe('2026-10-12'); // domingo → lunes anterior
    expect(weekdayInitial('2026-10-15')).toBe('J');
    expect(formatLongDate('2026-10-15')).toBe('jueves, 15 de octubre');
  });

  it('formatea la hora local descriptiva sin convertir zona', () => {
    expect(plain(formatLocalTime('2026-10-15T07:00:00'))).toMatch(/^7:00 a\. ?m\.$/);
    expect(plain(formatLocalTime('2026-10-15T14:30:00'))).toMatch(/^2:30 p\. ?m\.$/);
  });

  it('formatea un instante del contrato en la hora de Bogotá', () => {
    expect(plain(formatDateTime(Date.parse('2026-10-13T19:05:00Z')))).toMatch(
      /^martes, 13 de octubre, 2:05 p\. ?m\.$/,
    );
  });
});
