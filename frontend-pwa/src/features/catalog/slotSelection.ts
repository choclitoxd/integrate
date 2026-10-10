import { addDays, startOfWeek } from '@/shared/lib';

import type { ScheduleAvailability } from './api/types';

/** Selección que T-1.10b entrega a la reserva de T-1.10c (ADR-0012 §4). */
export interface SlotSelection {
  readonly experienceId: string;
  readonly scheduleId: string;
  readonly participants: number;
}

/** Tope de personas por reserva: `requestedSlots` ≤ 10 en `POST /bookings/lock` (ADR-0015 §2). */
export const MAX_PARTICIPANTS = 10;

export type DayStatus = 'outside' | 'none' | 'full' | 'available';

/** Franjas agrupadas por fecha local y ordenadas por hora de inicio. */
export function groupByDate(
  schedules: readonly ScheduleAvailability[],
): ReadonlyMap<string, readonly ScheduleAvailability[]> {
  const groups = new Map<string, ScheduleAvailability[]>();
  for (const schedule of schedules) {
    const day = groups.get(schedule.localDate);
    if (day) day.push(schedule);
    else groups.set(schedule.localDate, [schedule]);
  }
  for (const day of groups.values()) {
    day.sort((a, b) => a.localStartTime.localeCompare(b.localStartTime));
  }
  return groups;
}

export function fits(schedule: ScheduleAvailability, participants: number): boolean {
  return schedule.availableSlots >= participants;
}

/** Los 7 días (lunes a domingo) de la semana que empieza en `weekStart`. */
export function weekDays(weekStart: string): readonly string[] {
  return Array.from({ length: 7 }, (_, index) => addDays(weekStart, index));
}

export function dayStatus(
  date: string,
  byDate: ReadonlyMap<string, readonly ScheduleAvailability[]>,
  participants: number,
  range: { readonly from: string; readonly to: string },
): DayStatus {
  if (date < range.from || date > range.to) return 'outside';
  const day = byDate.get(date);
  if (!day || day.length === 0) return 'none';
  return day.some((schedule) => fits(schedule, participants)) ? 'available' : 'full';
}

/** Primera fecha con al menos una franja que alcance para el grupo; si no hay, la primera con franjas. */
export function firstUsefulDate(
  byDate: ReadonlyMap<string, readonly ScheduleAvailability[]>,
  participants: number,
): string | undefined {
  const dates = [...byDate.keys()].sort();
  return (
    dates.find((date) => byDate.get(date)?.some((schedule) => fits(schedule, participants))) ??
    dates[0]
  );
}

/** Lunes de la primera y la última semana navegables dentro de la ventana. */
export function weekBounds(range: { readonly from: string; readonly to: string }) {
  return { first: startOfWeek(range.from), last: startOfWeek(range.to) };
}

export type SelectionState =
  | { readonly kind: 'empty' }
  | { readonly kind: 'valid'; readonly schedule: ScheduleAvailability }
  /** La franja elegida desapareció o un refresco la dejó sin cupo suficiente. */
  | { readonly kind: 'lost'; readonly schedule: ScheduleAvailability | undefined };

/**
 * La selección se deriva de los datos vigentes en cada render: un refresco que agota la franja
 * la invalida sin efectos ni estados duplicados.
 */
export function resolveSelection(
  schedules: readonly ScheduleAvailability[],
  selectedId: string | null,
  participants: number,
): SelectionState {
  if (!selectedId) return { kind: 'empty' };
  const schedule = schedules.find((item) => item.scheduleId === selectedId);
  if (schedule && fits(schedule, participants)) return { kind: 'valid', schedule };
  return { kind: 'lost', schedule };
}
