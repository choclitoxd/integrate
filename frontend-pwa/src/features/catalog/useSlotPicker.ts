import { useMemo, useState } from 'react';

import { startOfWeek } from '@/shared/lib';

import type { ExperienceAvailability } from './api/types';
import {
  firstUsefulDate,
  groupByDate,
  MAX_PARTICIPANTS,
  resolveSelection,
  weekBounds,
} from './slotSelection';

/**
 * Estado del selector de fecha, franja y personas (T-1.10b), común a la vista móvil y a la de
 * escritorio (ADR-0020 §6). Sin efectos: la fecha y la semana visibles se derivan mientras el
 * turista no elija otras.
 */
export function useSlotPicker(availability: ExperienceAvailability) {
  const maxParticipants = Math.min(MAX_PARTICIPANTS, availability.maxCapacityPerSlot);
  const [participants, setParticipants] = useState(1);
  const [chosenDate, setChosenDate] = useState<string | null>(null);
  const [chosenWeek, setChosenWeek] = useState<string | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const range = { from: availability.dateFrom, to: availability.dateTo };
  const byDate = useMemo(() => groupByDate(availability.schedules), [availability.schedules]);
  const bounds = weekBounds(range);

  const date = chosenDate ?? firstUsefulDate(byDate, participants);
  const weekStart = chosenWeek ?? startOfWeek(date ?? range.from);
  const selection = resolveSelection(availability.schedules, selectedId, participants);
  const valid = selection.kind === 'valid' ? selection.schedule : undefined;

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
      setSelectedId(null);
    },
    selectSlot: (scheduleId: string) => {
      // Fija el día: cambiar luego el número de personas no debe moverlo.
      setChosenDate(date ?? null);
      setSelectedId(scheduleId);
    },
  };
}

export type SlotPickerState = ReturnType<typeof useSlotPicker>;
