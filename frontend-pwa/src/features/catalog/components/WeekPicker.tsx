import {
  addDays,
  dayOfMonth,
  formatLongDate,
  formatMonthYear,
  pluralize,
  weekdayInitial,
} from '@/shared/lib';
import { Icon } from '@/shared/ui';

import type { ScheduleAvailability } from '../api/types';
import { dayStatus, fits, weekDays, type DayStatus } from '../slotSelection';

interface WeekPickerProps {
  readonly weekStart: string;
  readonly canGoBack: boolean;
  readonly canGoForward: boolean;
  readonly onWeekChange: (weekStart: string) => void;
  readonly selectedDate: string | undefined;
  readonly onSelectDate: (date: string) => void;
  readonly byDate: ReadonlyMap<string, readonly ScheduleAvailability[]>;
  readonly participants: number;
  readonly range: { readonly from: string; readonly to: string };
}

const NAV_BUTTON =
  'inline-flex size-11 items-center justify-center rounded-lg text-brand hover:bg-brand-soft disabled:text-border-subtle disabled:hover:bg-transparent';

function monthLabel(days: readonly string[]): string {
  const first = days[0] ?? '';
  const last = days[days.length - 1] ?? first;
  const start = formatMonthYear(first);
  const end = formatMonthYear(last);
  return start === end ? start : `${start.replace(/ de \d{4}$/, '')} – ${end}`;
}

function describeDay(date: string, status: DayStatus, available: number, participants: number) {
  const day = formatLongDate(date);
  switch (status) {
    case 'available':
      return `${day}: ${pluralize(available, 'franja disponible', 'franjas disponibles')}`;
    case 'full':
      return participants === 1
        ? `${day}: agotado`
        : `${day}: sin cupo para ${String(participants)} personas`;
    case 'none':
      return `${day}: sin franjas`;
    case 'outside':
      return `${day}: fuera del periodo de reserva`;
  }
}

/**
 * Rejilla semanal de lunes a domingo (ADR-0012 §9): 7 × 44 px caben en 360 px sin scroll
 * horizontal. Los días agotados se tachan, además de cambiar de color.
 */
export function WeekPicker({
  weekStart,
  canGoBack,
  canGoForward,
  onWeekChange,
  selectedDate,
  onSelectDate,
  byDate,
  participants,
  range,
}: WeekPickerProps) {
  const days = weekDays(weekStart);

  return (
    <div>
      <div className="flex items-center justify-between gap-2">
        <button
          type="button"
          aria-label="Semana anterior"
          disabled={!canGoBack}
          onClick={() => {
            onWeekChange(addDays(weekStart, -7));
          }}
          className={NAV_BUTTON}
        >
          <Icon name="chevronLeft" size={24} />
        </button>
        <p aria-live="polite" className="font-semibold text-ink first-letter:uppercase">
          {monthLabel(days)}
        </p>
        <button
          type="button"
          aria-label="Semana siguiente"
          disabled={!canGoForward}
          onClick={() => {
            onWeekChange(addDays(weekStart, 7));
          }}
          className={NAV_BUTTON}
        >
          <Icon name="chevronRight" size={24} />
        </button>
      </div>

      <ul className="mt-2 grid grid-cols-7 gap-1" aria-label="Días de la semana">
        {days.map((date) => {
          const status = dayStatus(date, byDate, participants, range);
          const available =
            byDate.get(date)?.filter((schedule) => fits(schedule, participants)).length ?? 0;
          const selected = date === selectedDate;
          const selectable = status === 'available' || status === 'full';
          return (
            <li key={date}>
              <button
                type="button"
                aria-pressed={selected}
                aria-label={describeDay(date, status, available, participants)}
                disabled={!selectable}
                onClick={() => {
                  onSelectDate(date);
                }}
                className={`flex h-16 w-full min-w-11 flex-col items-center justify-center gap-0.5 rounded-lg border-2 font-semibold tabular-nums ${
                  selected
                    ? 'border-brand bg-brand text-on-brand'
                    : status === 'available'
                      ? 'border-brand-border bg-canvas text-ink hover:bg-brand-soft'
                      : status === 'full'
                        ? 'border-border-subtle bg-surface-subtle text-ink-muted'
                        : 'border-transparent text-ink-subtle'
                }`}
              >
                <span aria-hidden="true" className="text-xs font-medium">
                  {weekdayInitial(date)}
                </span>
                <span
                  aria-hidden="true"
                  className={`text-lg leading-none ${status === 'full' ? 'line-through decoration-2' : ''}`}
                >
                  {dayOfMonth(date)}
                </span>
                <span
                  aria-hidden="true"
                  className={`size-1.5 rounded-full ${
                    status === 'available' ? (selected ? 'bg-on-brand' : 'bg-brand') : ''
                  }`}
                />
              </button>
            </li>
          );
        })}
      </ul>

      <ul className="mt-3 flex flex-wrap gap-x-5 gap-y-1 text-sm text-ink-muted" aria-hidden="true">
        <li className="flex items-center gap-2">
          <span className="size-2 rounded-full bg-brand" /> Con cupos
        </li>
        <li className="flex items-center gap-2">
          <span className="font-semibold line-through decoration-2">15</span> Agotado
        </li>
      </ul>
    </div>
  );
}
