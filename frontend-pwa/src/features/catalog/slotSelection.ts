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

/* ---------- Filtros rápidos ---------- */

export type DayPeriod = 'morning' | 'afternoon' | 'evening';

export const DAY_PERIODS: readonly DayPeriod[] = ['morning', 'afternoon', 'evening'];

export const PERIOD_LABELS: Readonly<Record<DayPeriod, string>> = {
  morning: 'Mañana',
  afternoon: 'Tarde',
  evening: 'Noche',
};

/** Rango horario de cada período, para lectores de pantalla y ayudas. */
export const PERIOD_HINTS: Readonly<Record<DayPeriod, string>> = {
  morning: 'inician antes de las 12:00',
  afternoon: 'inician entre las 12:00 y las 17:59',
  evening: 'inician desde las 18:00',
};

/** Período por la hora local de inicio (`localStartTime` ya viene en hora de Colombia). */
export function periodOf(schedule: ScheduleAvailability): DayPeriod {
  const hour = Number(schedule.localStartTime.slice(11, 13));
  if (hour < 12) return 'morning';
  return hour < 18 ? 'afternoon' : 'evening';
}

/** Sábado o domingo: en la semana de lunes a domingo, desde el quinto día después del lunes. */
export function isWeekend(date: string): boolean {
  return date >= addDays(startOfWeek(date), 5);
}

export interface SlotFilters {
  /** Vacío = cualquier hora; varios = cualquiera de ellos. */
  readonly periods: readonly DayPeriod[];
  readonly weekendOnly: boolean;
}

export const NO_FILTERS: SlotFilters = { periods: [], weekendOnly: false };

export function hasFilters(filters: SlotFilters): boolean {
  return filters.periods.length > 0 || filters.weekendOnly;
}

export function matchesFilters(schedule: ScheduleAvailability, filters: SlotFilters): boolean {
  return (
    (filters.periods.length === 0 || filters.periods.includes(periodOf(schedule))) &&
    (!filters.weekendOnly || isWeekend(schedule.localDate))
  );
}

/* ---------- Calendario del mes ---------- */

/** "2026-10-17" → "2026-10-01". */
export function monthStart(date: string): string {
  return `${date.slice(0, 7)}-01`;
}

/** Primer día del mes desplazado `months` meses. */
export function addMonths(firstOfMonth: string, months: number): string {
  const year = Number(firstOfMonth.slice(0, 4));
  const month = Number(firstOfMonth.slice(5, 7)) - 1 + months;
  const y = year + Math.floor(month / 12);
  const m = ((month % 12) + 12) % 12;
  return `${String(y)}-${String(m + 1).padStart(2, '0')}-01`;
}

/**
 * Celdas del mes en semanas de lunes a domingo: `null` rellena antes del día 1 y después del último,
 * para que cada fecha caiga bajo su día de la semana.
 */
export function monthGrid(firstOfMonth: string): readonly (string | null)[] {
  const cells: (string | null)[] = [];
  for (let day = startOfWeek(firstOfMonth); day < firstOfMonth; day = addDays(day, 1)) {
    cells.push(null);
  }
  const month = firstOfMonth.slice(0, 7);
  for (let day = firstOfMonth; day.startsWith(month); day = addDays(day, 1)) cells.push(day);
  while (cells.length % 7 !== 0) cells.push(null);
  return cells;
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
