import { useId, useRef, useState } from 'react';

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
import {
  addMonths,
  dayStatus,
  fits,
  monthGrid,
  monthStart,
  weekDays,
  type DayStatus,
} from '../slotSelection';

interface DatePickerProps {
  readonly weekStart: string;
  readonly canGoBack: boolean;
  readonly canGoForward: boolean;
  readonly onWeekChange: (weekStart: string) => void;
  readonly selectedDate: string | undefined;
  readonly onSelectDate: (date: string) => void;
  readonly byDate: ReadonlyMap<string, readonly ScheduleAvailability[]>;
  readonly participants: number;
  readonly range: { readonly from: string; readonly to: string };
  /**
   * Vista móvil: el calendario ocupa todo el ancho de la columna (sin el margen de 16 px de
   * <main>), así 7 días de 44 px con 8 px de separación caben desde 356 px (ADR-0020 §1).
   */
  readonly bleed?: boolean;
}

const NAV_BUTTON =
  'inline-flex size-11 shrink-0 items-center justify-center rounded-lg text-brand hover:bg-brand-soft disabled:text-border-subtle disabled:hover:bg-transparent';

const WEEKDAY_HEADERS = ['L', 'M', 'M', 'J', 'V', 'S', 'D'];

function weekLabel(days: readonly string[]): string {
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

interface DayButtonProps {
  readonly date: string;
  readonly selected: boolean;
  readonly byDate: DatePickerProps['byDate'];
  readonly participants: number;
  readonly range: DatePickerProps['range'];
  readonly compact: boolean;
  readonly onSelect: (date: string) => void;
}

/** Un día: los agotados se tachan y los que tienen cupo llevan un punto, además del color. */
function DayButton({
  date,
  selected,
  byDate,
  participants,
  range,
  compact,
  onSelect,
}: DayButtonProps) {
  const status = dayStatus(date, byDate, participants, range);
  const available =
    byDate.get(date)?.filter((schedule) => fits(schedule, participants)).length ?? 0;
  const selectable = status === 'available' || status === 'full';
  return (
    <button
      type="button"
      aria-pressed={selected}
      aria-label={describeDay(date, status, available, participants)}
      disabled={!selectable}
      onClick={() => {
        onSelect(date);
      }}
      className={`flex w-full min-w-11 flex-col items-center justify-center gap-0.5 rounded-lg border-2 font-semibold tabular-nums ${
        compact ? 'h-12' : 'h-16'
      } ${
        selected
          ? 'border-brand bg-brand text-on-brand'
          : status === 'available'
            ? 'border-brand-border bg-canvas text-ink hover:bg-brand-soft'
            : status === 'full'
              ? 'border-border-subtle bg-surface-subtle text-ink-muted'
              : 'border-transparent text-ink-subtle'
      }`}
    >
      {!compact && (
        <span aria-hidden="true" className="text-xs font-medium">
          {weekdayInitial(date)}
        </span>
      )}
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
  );
}

/**
 * Selector de fecha (ADR-0012 §9): una semana de lunes a domingo y, al tocar el mes, el calendario
 * del mes completo. Al elegir un día en el mes se vuelve a la semana de ese día, con sus franjas
 * debajo. 7 × 44 px con 8 px de separación piden 356 px: en columnas más angostas la semana pasa a
 * 4 + 3 y el mes reduce la separación a 2 px sin bajar de 44 px por día (ADR-0020 §1).
 */
export function DatePicker({
  weekStart,
  canGoBack,
  canGoForward,
  onWeekChange,
  selectedDate,
  onSelectDate,
  byDate,
  participants,
  range,
  bleed = false,
}: DatePickerProps) {
  const calendarId = useId();
  const toggleRef = useRef<HTMLButtonElement>(null);
  const [expanded, setExpanded] = useState(false);
  const [visibleMonth, setVisibleMonth] = useState(() => monthStart(weekStart));

  const days = weekDays(weekStart);
  const firstMonth = monthStart(range.from);
  const lastMonth = monthStart(range.to);
  const label = expanded ? formatMonthYear(visibleMonth) : weekLabel(days);

  function toggle() {
    if (!expanded) setVisibleMonth(monthStart(selectedDate ?? weekStart));
    setExpanded(!expanded);
  }

  const dayProps = { byDate, participants, range };

  return (
    <div className={`@container ${bleed ? '-mx-4' : ''}`}>
      <div className={`flex items-center justify-between gap-2 ${bleed ? 'px-2' : ''}`}>
        <button
          type="button"
          aria-label={expanded ? 'Mes anterior' : 'Semana anterior'}
          disabled={expanded ? visibleMonth <= firstMonth : !canGoBack}
          onClick={() => {
            if (expanded) setVisibleMonth(addMonths(visibleMonth, -1));
            else onWeekChange(addDays(weekStart, -7));
          }}
          className={NAV_BUTTON}
        >
          <Icon name="chevronLeft" size={24} />
        </button>

        <button
          ref={toggleRef}
          type="button"
          aria-expanded={expanded}
          aria-controls={calendarId}
          onClick={toggle}
          className="inline-flex min-h-11 min-w-0 items-center gap-1.5 rounded-full bg-surface px-4 font-semibold text-ink hover:bg-brand-soft"
        >
          <span className="min-w-0 truncate first-letter:uppercase">{label}</span>
          <span className="sr-only">
            {expanded ? ', ver solo la semana' : ', ver el mes completo'}
          </span>
          <Icon
            name="chevronDown"
            size={20}
            className={`text-brand transition-transform duration-150 ${expanded ? 'rotate-180' : ''}`}
          />
        </button>

        <button
          type="button"
          aria-label={expanded ? 'Mes siguiente' : 'Semana siguiente'}
          disabled={expanded ? visibleMonth >= lastMonth : !canGoForward}
          onClick={() => {
            if (expanded) setVisibleMonth(addMonths(visibleMonth, 1));
            else onWeekChange(addDays(weekStart, 7));
          }}
          className={NAV_BUTTON}
        >
          <Icon name="chevronRight" size={24} />
        </button>
      </div>
      <p aria-live="polite" className="sr-only">
        {expanded ? `Calendario de ${label}` : `Semana de ${label}`}
      </p>

      <div id={calendarId} className="mt-2">
        {expanded ? (
          <>
            <div
              aria-hidden="true"
              className="grid grid-cols-7 gap-0.5 text-center text-xs font-semibold text-ink-muted @min-[22.25rem]:gap-2"
            >
              {WEEKDAY_HEADERS.map((day, index) => (
                <span key={String(index)} className="py-1">
                  {day}
                </span>
              ))}
            </div>
            <ul
              aria-label={`Días de ${formatMonthYear(visibleMonth)}`}
              className="grid grid-cols-7 gap-0.5 @min-[22.25rem]:gap-2"
            >
              {monthGrid(visibleMonth).map((date, index) =>
                date ? (
                  <li key={date}>
                    <DayButton
                      {...dayProps}
                      date={date}
                      compact
                      selected={date === selectedDate}
                      onSelect={(next) => {
                        onSelectDate(next);
                        setExpanded(false);
                        toggleRef.current?.focus();
                      }}
                    />
                  </li>
                ) : (
                  <li key={`vacio-${String(index)}`} aria-hidden="true" />
                ),
              )}
            </ul>
          </>
        ) : (
          <ul
            className="grid grid-cols-4 gap-2 @min-[22.25rem]:grid-cols-7"
            aria-label="Días de la semana"
          >
            {days.map((date) => (
              <li key={date}>
                <DayButton
                  {...dayProps}
                  date={date}
                  compact={false}
                  selected={date === selectedDate}
                  onSelect={onSelectDate}
                />
              </li>
            ))}
          </ul>
        )}
      </div>

      <ul
        className={`mt-3 flex flex-wrap gap-x-5 gap-y-1 text-sm text-ink-muted ${bleed ? 'px-4' : ''}`}
        aria-hidden="true"
      >
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
