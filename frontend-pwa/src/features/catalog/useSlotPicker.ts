import { useMemo, useState } from 'react';

import { startOfWeek } from '@/shared/lib';

import type { ExperienceAvailability } from './api/types';
import {
  DAY_PERIODS,
  firstUsefulDate,
  fits,
  groupByDate,
  hasFilters,
  isWeekend,
  matchesFilters,
  MAX_PARTICIPANTS,
  NO_FILTERS,
  periodOf,
  resolveSelection,
  weekBounds,
  type DayPeriod,
  type SlotFilters,
} from './slotSelection';

/**
 * Estado del selector de fecha, franja y personas (T-1.10b), común a la vista móvil y a la de
 * escritorio (ADR-0020 §6). Sin efectos: la fecha y la semana visibles se derivan mientras el
 * turista no elija otras. Los filtros rápidos (período del día, fin de semana) recortan las franjas
 * que ven el calendario y la lista; la selección se valida siempre contra todas.
 */
export function useSlotPicker(availability: ExperienceAvailability) {
  const maxParticipants = Math.min(MAX_PARTICIPANTS, availability.maxCapacityPerSlot);
  const [participants, setParticipants] = useState(1);
  const [chosenDate, setChosenDate] = useState<string | null>(null);
  const [chosenWeek, setChosenWeek] = useState<string | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [filters, setFilters] = useState<SlotFilters>(NO_FILTERS);

  const range = { from: availability.dateFrom, to: availability.dateTo };
  const visible = useMemo(
    () => availability.schedules.filter((schedule) => matchesFilters(schedule, filters)),
    [availability.schedules, filters],
  );
  const byDate = useMemo(() => groupByDate(visible), [visible]);
  const bounds = weekBounds(range);

  const date = chosenDate ?? firstUsefulDate(byDate, participants);
  const weekStart = chosenWeek ?? startOfWeek(date ?? range.from);
  const selection = resolveSelection(availability.schedules, selectedId, participants);
  const valid = selection.kind === 'valid' ? selection.schedule : undefined;
  const firstDate = firstUsefulDate(byDate, participants);

  // Conteos de los filtros: franjas con cupo para el grupo que quedarían al activar cada uno.
  const withRoom = availability.schedules.filter((schedule) => fits(schedule, participants));
  const periodOptions = DAY_PERIODS.filter((period) =>
    availability.schedules.some((schedule) => periodOf(schedule) === period),
  ).map((period) => ({
    period,
    count: withRoom.filter(
      (schedule) =>
        periodOf(schedule) === period && (!filters.weekendOnly || isWeekend(schedule.localDate)),
    ).length,
  }));
  const weekendSchedules = availability.schedules.filter((s) => isWeekend(s.localDate)).length;
  const weekendCount = withRoom.filter(
    (schedule) =>
      isWeekend(schedule.localDate) &&
      (filters.periods.length === 0 || filters.periods.includes(periodOf(schedule))),
  ).length;

  /** Cambiar filtros vuelve a la primera fecha útil y suelta la franja elegida. */
  function applyFilters(next: SlotFilters) {
    setFilters(next);
    setChosenDate(null);
    setChosenWeek(null);
    setSelectedId(null);
  }

  return {
    maxParticipants,
    participants,
    setParticipants,
    range,
    byDate,
    date,
    daySchedules: date ? (byDate.get(date) ?? []) : [],
    selection,
    selectedId,
    valid,
    total: availability.unitPriceCop * participants,
    week: {
      weekStart,
      canGoBack: weekStart > bounds.first,
      canGoForward: weekStart < bounds.last,
      onWeekChange: setChosenWeek,
    },
    selectDate: (next: string) => {
      setChosenDate(next);
      // Elegir un día desde el mes completo deja visible su semana al plegar el calendario.
      setChosenWeek(startOfWeek(next));
      setSelectedId(null);
    },
    selectSlot: (scheduleId: string) => {
      // Fija el día: cambiar luego el número de personas no debe moverlo.
      setChosenDate(date ?? null);
      setSelectedId(scheduleId);
    },
    filters: {
      value: filters,
      active: hasFilters(filters),
      /** Ningún filtro cambiaría lo que se ve: la barra no se muestra. */
      useful:
        periodOptions.length > 1 ||
        (weekendSchedules > 0 && weekendSchedules < availability.schedules.length),
      periodOptions,
      weekendAvailable: weekendSchedules > 0,
      weekendCount,
      noMatches: hasFilters(filters) && visible.length === 0,
      togglePeriod: (period: DayPeriod) => {
        applyFilters({
          ...filters,
          periods: filters.periods.includes(period)
            ? filters.periods.filter((p) => p !== period)
            : [...filters.periods, period],
        });
      },
      toggleWeekend: () => {
        applyFilters({ ...filters, weekendOnly: !filters.weekendOnly });
      },
      clear: () => {
        applyFilters(NO_FILTERS);
      },
    },
    /** Primera fecha con cupo para el grupo (respetando los filtros), o `undefined`. */
    firstDate,
    goToFirstDate: () => {
      if (!firstDate) return;
      setChosenDate(firstDate);
      setChosenWeek(startOfWeek(firstDate));
      setSelectedId(null);
    },
  };
}

export type SlotPickerState = ReturnType<typeof useSlotPicker>;
