import { describe, expect, it } from 'vitest';

import type { ScheduleAvailability } from './api/types';
import {
  addMonths,
  dayStatus,
  firstUsefulDate,
  groupByDate,
  isWeekend,
  matchesFilters,
  monthGrid,
  monthStart,
  NO_FILTERS,
  periodOf,
  resolveSelection,
  weekBounds,
  weekDays,
} from './slotSelection';

function slot(
  id: string,
  localDate: string,
  hour: string,
  available: number,
): ScheduleAvailability {
  return {
    scheduleId: id,
    localDate,
    startTime: `${localDate}T${hour}:00Z`,
    endTime: `${localDate}T${hour}:00Z`,
    localStartTime: `${localDate}T${hour}:00`,
    localEndTime: `${localDate}T${hour}:00`,
    totalSlots: 12,
    availableSlots: available,
    status: 'OPEN',
  };
}

const RANGE = { from: '2026-10-15', to: '2026-12-13' };

describe('slotSelection', () => {
  const schedules = [
    slot('b', '2026-10-16', '14:00', 0),
    slot('a', '2026-10-16', '07:00', 0),
    slot('c', '2026-10-17', '07:00', 2),
    slot('d', '2026-10-18', '07:00', 8),
  ];
  const byDate = groupByDate(schedules);

  it('agrupa por fecha y ordena por hora local', () => {
    expect(byDate.get('2026-10-16')?.map((s) => s.scheduleId)).toEqual(['a', 'b']);
  });

  it('clasifica cada día según el cupo que necesita el grupo', () => {
    expect(dayStatus('2026-10-14', byDate, 1, RANGE)).toBe('outside');
    expect(dayStatus('2026-10-15', byDate, 1, RANGE)).toBe('none');
    expect(dayStatus('2026-10-16', byDate, 1, RANGE)).toBe('full');
    expect(dayStatus('2026-10-17', byDate, 2, RANGE)).toBe('available');
    expect(dayStatus('2026-10-17', byDate, 3, RANGE)).toBe('full');
  });

  it('propone la primera fecha que alcanza para el grupo', () => {
    expect(firstUsefulDate(byDate, 1)).toBe('2026-10-17');
    expect(firstUsefulDate(byDate, 5)).toBe('2026-10-18');
    expect(firstUsefulDate(byDate, 20)).toBe('2026-10-16');
    expect(firstUsefulDate(new Map(), 1)).toBeUndefined();
  });

  it('arma semanas de lunes a domingo dentro de la ventana', () => {
    expect(weekDays('2026-10-12')).toEqual([
      '2026-10-12',
      '2026-10-13',
      '2026-10-14',
      '2026-10-15',
      '2026-10-16',
      '2026-10-17',
      '2026-10-18',
    ]);
    expect(weekBounds(RANGE)).toEqual({ first: '2026-10-12', last: '2026-12-07' });
  });

  it('invalida la selección si un refresco deja la franja sin cupo suficiente', () => {
    expect(resolveSelection(schedules, null, 1)).toEqual({ kind: 'empty' });
    expect(resolveSelection(schedules, 'c', 2).kind).toBe('valid');
    expect(resolveSelection(schedules, 'c', 3)).toMatchObject({ kind: 'lost' });
    expect(resolveSelection(schedules, 'zzz', 1)).toEqual({ kind: 'lost', schedule: undefined });
  });
});

describe('filtros rápidos y calendario del mes', () => {
  it('clasifica la franja por la hora local de inicio', () => {
    expect(periodOf(slot('x', '2026-10-17', '07:00', 1))).toBe('morning');
    expect(periodOf(slot('x', '2026-10-17', '11:59', 1))).toBe('morning');
    expect(periodOf(slot('x', '2026-10-17', '12:00', 1))).toBe('afternoon');
    expect(periodOf(slot('x', '2026-10-17', '17:59', 1))).toBe('afternoon');
    expect(periodOf(slot('x', '2026-10-17', '18:00', 1))).toBe('evening');
  });

  it('reconoce sábado y domingo', () => {
    expect(isWeekend('2026-10-16')).toBe(false); // viernes
    expect(isWeekend('2026-10-17')).toBe(true); // sábado
    expect(isWeekend('2026-10-18')).toBe(true); // domingo
    expect(isWeekend('2026-10-19')).toBe(false); // lunes
  });

  it('combina períodos con O y el fin de semana con Y', () => {
    const saturdayAfternoon = slot('s', '2026-10-17', '15:00', 3);
    const fridayMorning = slot('f', '2026-10-16', '08:00', 3);
    expect(matchesFilters(fridayMorning, NO_FILTERS)).toBe(true);
    const filters = { periods: ['morning', 'afternoon'] as const, weekendOnly: false };
    expect(matchesFilters(fridayMorning, filters)).toBe(true);
    expect(matchesFilters(saturdayAfternoon, filters)).toBe(true);
    expect(matchesFilters(fridayMorning, { ...filters, weekendOnly: true })).toBe(false);
    expect(matchesFilters(saturdayAfternoon, { periods: ['morning'], weekendOnly: true })).toBe(
      false,
    );
  });

  it('calcula meses y su rejilla de lunes a domingo', () => {
    expect(monthStart('2026-10-17')).toBe('2026-10-01');
    expect(addMonths('2026-12-01', 1)).toBe('2027-01-01');
    expect(addMonths('2026-01-01', -1)).toBe('2025-12-01');

    // Octubre de 2026 empieza en jueves: tres huecos antes del 1, 31 días y relleno hasta 35.
    const october = monthGrid('2026-10-01');
    expect(october).toHaveLength(35);
    expect(october.slice(0, 4)).toEqual([null, null, null, '2026-10-01']);
    expect(october[33]).toBe('2026-10-31');
    expect(october[34]).toBeNull();

    // Febrero de 2027 empieza en lunes y tiene 28 días: cuatro semanas exactas.
    const february = monthGrid('2027-02-01');
    expect(february).toHaveLength(28);
    expect(february[0]).toBe('2027-02-01');
    expect(february[27]).toBe('2027-02-28');
  });
});
