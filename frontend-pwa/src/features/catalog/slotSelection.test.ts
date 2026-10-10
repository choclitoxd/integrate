import { describe, expect, it } from 'vitest';

import type { ScheduleAvailability } from './api/types';
import {
  dayStatus,
  firstUsefulDate,
  groupByDate,
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
