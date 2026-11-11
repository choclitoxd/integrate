import { useCallback, useEffect, useId, useRef, useState, type ReactNode } from 'react';

import {
  addDays,
  addMonths,
  dayOfMonth,
  formatLongDate,
  formatMonthYear,
  monthGrid,
  monthStart,
  todayInBogota,
  toTitleCase,
} from '@/shared/lib';

import { FIELD_WRAPPER_CLASS, fieldBorderClass, INPUT_CLASS } from './fieldStyles';
import { Icon } from './Icon';

export interface DatePickerFieldProps {
  readonly label: string;
  readonly value: string; // ISO 'YYYY-MM-DD' o ''
  readonly onChange: (value: string) => void;
  readonly name?: string;
  readonly hint?: ReactNode;
  readonly error?: string | undefined;
  readonly minDate?: string;
  readonly maxDate?: string;
  readonly placeholder?: string;
  readonly disabled?: boolean;
  readonly className?: string;
}

const CALENDAR_HEIGHT_ESTIMATE_PX = 360;

function scrollParentOf(element: HTMLElement): HTMLElement | undefined {
  for (let parent = element.parentElement; parent; parent = parent.parentElement) {
    const { overflowY } = window.getComputedStyle(parent);
    if (overflowY === 'auto' || overflowY === 'scroll') return parent;
  }
  return undefined;
}

const WEEKDAY_HEADERS = [
  { short: 'Lu', full: 'Lunes' },
  { short: 'Ma', full: 'Martes' },
  { short: 'Mi', full: 'Miércoles' },
  { short: 'Ju', full: 'Jueves' },
  { short: 'Vi', full: 'Viernes' },
  { short: 'Sá', full: 'Sábado' },
  { short: 'Do', full: 'Domingo' },
] as const;

/**
 * Campo selector de fecha accesible con calendario desplegable integrado.
 * Cumple con FE-DIR-001 (tokens certificados de contraste solar AAA, áreas táctiles ≥ 44 px,
 * navegación por teclado y WAI-ARIA Dialog).
 */
export function DatePickerField({
  label,
  value,
  onChange,
  name,
  hint,
  error,
  minDate,
  maxDate,
  placeholder = 'dd/mm/aaaa',
  disabled = false,
  className = '',
}: DatePickerFieldProps) {
  const baseId = useId();
  const id = `${baseId}-input`;
  const dialogId = `${baseId}-dialog`;
  const hintId = `${baseId}-hint`;
  const errorId = `${baseId}-error`;

  const rootRef = useRef<HTMLDivElement>(null);
  const fieldRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const [open, setOpen] = useState(false);
  const [openUp, setOpenUp] = useState(false);

  const today = todayInBogota();
  const initialMonth = value ? monthStart(value) : monthStart(today);
  const [visibleMonth, setVisibleMonth] = useState(initialMonth);

  const measure = useCallback(() => {
    const field = fieldRef.current;
    if (!field) return;
    const rect = field.getBoundingClientRect();
    const scroller = scrollParentOf(field);
    const viewport = window.visualViewport;
    const screenBottom = viewport ? viewport.offsetTop + viewport.height : window.innerHeight;
    const bounds = scroller?.getBoundingClientRect() ?? { top: 0, bottom: screenBottom };
    const scrollable = scroller
      ? Math.max(scroller.scrollHeight - scroller.clientHeight - scroller.scrollTop, 0)
      : 0;
    const below = Math.min(bounds.bottom, screenBottom) - rect.bottom + scrollable;
    const above = rect.top - bounds.top;
    const up = below < CALENDAR_HEIGHT_ESTIMATE_PX && above > below;
    setOpenUp(up);
  }, []);

  const openCalendar = useCallback(() => {
    if (disabled) return;
    if (value) {
      setVisibleMonth(monthStart(value));
    }
    measure();
    setOpen(true);
  }, [disabled, measure, value]);

  const closeCalendar = useCallback(() => {
    setOpen(false);
  }, []);

  useEffect(() => {
    if (!open) return;
    function handlePointerDown(event: PointerEvent) {
      if (!rootRef.current?.contains(event.target as Node)) {
        closeCalendar();
      }
    }
    function handleGlobalKeyDown(event: globalThis.KeyboardEvent) {
      if (event.key === 'Escape') {
        event.preventDefault();
        closeCalendar();
        inputRef.current?.focus();
      }
    }
    document.addEventListener('pointerdown', handlePointerDown);
    document.addEventListener('keydown', handleGlobalKeyDown);
    window.visualViewport?.addEventListener('resize', measure);
    return () => {
      document.removeEventListener('pointerdown', handlePointerDown);
      document.removeEventListener('keydown', handleGlobalKeyDown);
      window.visualViewport?.removeEventListener('resize', measure);
    };
  }, [open, measure, closeCalendar]);

  const describedBy = [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(' ');

  const monthLabel = toTitleCase(formatMonthYear(visibleMonth));
  const cells = monthGrid(visibleMonth);

  return (
    <div ref={rootRef} className={className}>
      <label htmlFor={id} className="block font-semibold text-ink">
        {label}
      </label>

      {hint && (
        <p id={hintId} className="mt-0.5 text-sm text-ink-muted">
          {hint}
        </p>
      )}

      <div ref={fieldRef} className="relative mt-1.5">
        <div
          className={`${FIELD_WRAPPER_CLASS} ${fieldBorderClass(Boolean(error))} ${
            disabled ? 'opacity-60 pointer-events-none' : ''
          }`}
        >
          <input
            ref={inputRef}
            id={id}
            name={name}
            type="text"
            role="combobox"
            aria-expanded={open}
            aria-haspopup="dialog"
            aria-controls={open ? dialogId : undefined}
            aria-invalid={error ? true : undefined}
            aria-describedby={describedBy || undefined}
            disabled={disabled}
            placeholder={placeholder}
            value={value}
            onChange={(event) => {
              onChange(event.target.value);
            }}
            onClick={() => {
              if (!open) openCalendar();
            }}
            onFocus={() => {
              if (!open) openCalendar();
            }}
            className={`${INPUT_CLASS} cursor-pointer`}
          />

          <div className="flex shrink-0 items-center pr-1.5">
            {value !== '' && !disabled && (
              <button
                type="button"
                aria-label="Quitar fecha"
                onClick={(event) => {
                  event.stopPropagation();
                  onChange('');
                  inputRef.current?.focus();
                }}
                className="inline-flex size-9 items-center justify-center rounded-full text-ink-muted hover:bg-surface-subtle hover:text-ink focus-visible:outline-none"
              >
                <Icon name="close" size={18} />
              </button>
            )}

            <button
              type="button"
              aria-label={open ? 'Cerrar calendario' : 'Abrir calendario'}
              aria-expanded={open}
              aria-controls={open ? dialogId : undefined}
              disabled={disabled}
              onClick={() => {
                if (open) closeCalendar();
                else openCalendar();
              }}
              className="inline-flex size-11 items-center justify-center rounded-lg text-brand hover:bg-brand-soft focus-visible:outline-none"
            >
              <Icon name="calendar" size={22} />
            </button>
          </div>
        </div>

        {open && (
          <div
            id={dialogId}
            role="dialog"
            aria-modal="true"
            aria-label={`Calendario de ${monthLabel}`}
            className={`absolute z-30 w-72 sm:w-80 rounded-2xl border-2 border-border bg-surface-elevated p-4 shadow-xl ${
              openUp ? 'bottom-full mb-2' : 'top-full mt-2'
            }`}
          >
            {/* Cabecera del mes con navegación */}
            <div className="flex items-center justify-between pb-3 border-b border-border-subtle">
              <button
                type="button"
                aria-label="Mes anterior"
                onClick={() => {
                  setVisibleMonth(addMonths(visibleMonth, -1));
                }}
                className="inline-flex size-11 items-center justify-center rounded-lg text-brand hover:bg-brand-soft focus-visible:outline-none"
              >
                <Icon name="chevronLeft" size={22} />
              </button>
              <span className="text-base font-bold text-ink">{monthLabel}</span>
              <button
                type="button"
                aria-label="Mes siguiente"
                onClick={() => {
                  setVisibleMonth(addMonths(visibleMonth, 1));
                }}
                className="inline-flex size-11 items-center justify-center rounded-lg text-brand hover:bg-brand-soft focus-visible:outline-none"
              >
                <Icon name="chevronRight" size={22} />
              </button>
            </div>

            {/* Días de la semana */}
            <div
              className="grid grid-cols-7 gap-1 pt-2 pb-1 text-center text-xs font-semibold text-ink-muted"
              aria-hidden="true"
            >
              {WEEKDAY_HEADERS.map((day) => (
                <span key={day.short} title={day.full}>
                  {day.short}
                </span>
              ))}
            </div>

            {/* Cuadrícula de días */}
            <div role="grid" className="grid grid-cols-7 gap-1">
              {cells.map((date, index) => {
                if (!date) {
                  return (
                    <div
                      key={`empty-${String(index)}`}
                      aria-hidden="true"
                      className="size-9 sm:size-10"
                    />
                  );
                }
                const isSelected = date === value;
                const isToday = date === today;
                const isBeforeMin = Boolean(minDate) && Boolean(date && minDate && date < minDate);
                const isAfterMax = Boolean(maxDate) && Boolean(date && maxDate && date > maxDate);
                const isDisabled = isBeforeMin || isAfterMax;

                return (
                  <button
                    key={date}
                    type="button"
                    disabled={isDisabled}
                    aria-label={formatLongDate(date)}
                    aria-pressed={isSelected}
                    aria-current={isToday ? 'date' : undefined}
                    onClick={() => {
                      onChange(date);
                      closeCalendar();
                      inputRef.current?.focus();
                    }}
                    className={`flex size-9 sm:size-10 items-center justify-center rounded-lg text-sm font-semibold tabular-nums transition-colors ${
                      isSelected
                        ? 'bg-brand text-on-brand font-bold shadow-sm'
                        : isToday
                          ? 'border-2 border-brand text-brand hover:bg-brand-soft'
                          : 'text-ink hover:bg-brand-soft'
                    } ${isDisabled ? 'text-ink-subtle opacity-40 cursor-not-allowed' : ''}`}
                  >
                    {dayOfMonth(date)}
                  </button>
                );
              })}
            </div>

            {/* Acciones rápidas al pie */}
            <div className="mt-3 flex items-center justify-between gap-1.5 border-t border-border-subtle pt-3">
              <div className="flex flex-wrap gap-1.5">
                <button
                  type="button"
                  onClick={() => {
                    onChange(today);
                    closeCalendar();
                    inputRef.current?.focus();
                  }}
                  className="inline-flex min-h-11 items-center rounded-full bg-brand-soft px-3 text-xs font-semibold text-brand hover:bg-brand hover:text-on-brand transition-colors"
                >
                  Hoy
                </button>
                <button
                  type="button"
                  onClick={() => {
                    onChange(addDays(today, 1));
                    closeCalendar();
                    inputRef.current?.focus();
                  }}
                  className="inline-flex min-h-11 items-center rounded-full bg-surface px-3 text-xs font-semibold text-ink hover:bg-surface-subtle transition-colors"
                >
                  Mañana
                </button>
              </div>

              <div className="flex gap-1.5">
                {value !== '' && (
                  <button
                    type="button"
                    onClick={() => {
                      onChange('');
                      closeCalendar();
                      inputRef.current?.focus();
                    }}
                    className="inline-flex min-h-11 items-center rounded-full px-2.5 text-xs font-semibold text-danger-text hover:bg-surface-subtle transition-colors"
                  >
                    Quitar
                  </button>
                )}
                <button
                  type="button"
                  onClick={() => {
                    closeCalendar();
                    inputRef.current?.focus();
                  }}
                  className="inline-flex min-h-11 items-center rounded-full px-2.5 text-xs font-semibold text-ink-muted hover:text-ink transition-colors"
                >
                  Cerrar
                </button>
              </div>
            </div>
          </div>
        )}
      </div>

      {error && (
        <p
          id={errorId}
          className="mt-1 flex items-start gap-1.5 text-sm font-semibold text-danger-text"
        >
          <Icon name="alert" size={18} className="mt-px" />
          {error}
        </p>
      )}
    </div>
  );
}
