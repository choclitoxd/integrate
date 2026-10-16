import { Link } from 'react-router';

import { addDays, formatClockTime, formatCop, formatLocalTime, pluralize } from '@/shared/lib';
import { Button, buttonClassName, Icon } from '@/shared/ui';

import type { ExperienceAvailability } from '../api/types';
import { MAX_PARTICIPANTS, PERIOD_HINTS, PERIOD_LABELS } from '../slotSelection';
import type { SlotPickerState } from '../useSlotPicker';
import { DatePicker } from './DatePicker';
import { ParticipantStepper } from './ParticipantStepper';
import { SlotList } from './SlotList';

/** Piezas del selector de franjas (T-1.10b) que comparten la vista móvil y la de escritorio. */

export function NoSchedules({ experienceId }: { experienceId: string }) {
  return (
    <div className="mt-6 rounded-xl bg-surface px-4 py-6 text-center">
      <p className="font-semibold text-ink">
        Por ahora no hay franjas abiertas en los próximos 60 días.
      </p>
      <p className="mt-1 text-ink-muted">
        La comunidad publica nuevas fechas con frecuencia. Vuelve a consultar pronto.
      </p>
      <Link to={`/experiencias/${experienceId}`} className={buttonClassName('secondary', 'mt-4')}>
        Volver a la experiencia
      </Link>
    </div>
  );
}

export function ParticipantsBox({
  picker,
  availability,
}: {
  picker: SlotPickerState;
  availability: ExperienceAvailability;
}) {
  return (
    <div className="rounded-xl bg-surface px-4 py-4">
      <ParticipantStepper
        value={picker.participants}
        max={picker.maxParticipants}
        onChange={picker.setParticipants}
      />
      {availability.maxCapacityPerSlot > MAX_PARTICIPANTS && (
        <p className="mt-3 text-sm text-ink-muted">
          Para grupos de más de {MAX_PARTICIPANTS} personas, haz varias reservas.
        </p>
      )}
    </div>
  );
}

export function DateSection({
  picker,
  bleed = false,
}: {
  picker: SlotPickerState;
  bleed?: boolean;
}) {
  return (
    <section aria-labelledby="fechas">
      <h2 id="fechas" className="sr-only">
        Fecha
      </h2>
      <DatePicker
        {...picker.week}
        selectedDate={picker.date}
        onSelectDate={picker.selectDate}
        byDate={picker.byDate}
        participants={picker.participants}
        range={picker.range}
        bleed={bleed}
      />
    </section>
  );
}

const CHIP =
  'inline-flex min-h-11 items-center gap-1.5 rounded-full border-2 border-border bg-canvas px-4 font-semibold text-ink hover:bg-brand-soft aria-pressed:border-brand aria-pressed:bg-brand aria-pressed:text-on-brand';

/**
 * Filtros rápidos sobre las franjas (período del día y fin de semana) y atajo a la primera fecha con
 * cupo. Cada filtro dice cuántas franjas con cupo para el grupo encontraría. No se muestran si
 * ninguno cambiaría el resultado (p. ej. todas las franjas son de mañana).
 */
export function SlotFiltersBar({ picker }: { picker: SlotPickerState }) {
  const { filters } = picker;
  // El atajo sobra si la primera fecha útil ya está elegida y a la vista en la semana.
  const { weekStart } = picker.week;
  const firstIsShown =
    picker.firstDate !== undefined &&
    picker.firstDate === picker.date &&
    picker.firstDate >= weekStart &&
    picker.firstDate < addDays(weekStart, 7);

  return (
    <div className="grid gap-3">
      {filters.useful && (
        <fieldset>
          <legend className="font-semibold text-ink">Encuentra tu franja más rápido</legend>
          <div className="mt-2 flex flex-wrap gap-2">
            {filters.periodOptions.map(({ period, count }) => (
              <button
                key={period}
                type="button"
                aria-pressed={filters.value.periods.includes(period)}
                onClick={() => {
                  filters.togglePeriod(period);
                }}
                className={CHIP}
              >
                {PERIOD_LABELS[period]}
                <span className="sr-only">, franjas que {PERIOD_HINTS[period]},</span>
                <span className="font-normal tabular-nums opacity-90">· {count}</span>
              </button>
            ))}
            {filters.weekendAvailable && (
              <button
                type="button"
                aria-pressed={filters.value.weekendOnly}
                onClick={filters.toggleWeekend}
                className={CHIP}
              >
                Fin de semana
                <span className="sr-only">, sábado y domingo,</span>
                <span className="font-normal tabular-nums opacity-90">
                  · {filters.weekendCount}
                </span>
              </button>
            )}
          </div>
        </fieldset>
      )}

      <div className="flex flex-wrap gap-x-4 gap-y-1">
        {picker.firstDate && !firstIsShown && (
          <button
            type="button"
            onClick={picker.goToFirstDate}
            className="inline-flex min-h-11 items-center gap-1.5 font-semibold text-brand underline underline-offset-4"
          >
            <Icon name="calendar" size={20} />
            Primera fecha con cupo
          </button>
        )}
        {filters.active && (
          <button
            type="button"
            onClick={filters.clear}
            className="inline-flex min-h-11 items-center gap-1.5 font-semibold text-brand underline underline-offset-4"
          >
            <Icon name="close" size={20} />
            Quitar filtros
          </button>
        )}
      </div>
    </div>
  );
}

/** Con los filtros activos no queda ninguna franja en la ventana de 60 días. */
export function NoFilterMatches({ picker }: { picker: SlotPickerState }) {
  return (
    <div role="status" className="rounded-xl bg-surface px-4 py-6 text-center">
      <p className="font-semibold text-ink">Ninguna franja coincide con estos filtros.</p>
      <p className="mt-1 text-ink-muted">Prueba con otro horario o quita los filtros.</p>
      <Button variant="secondary" className="mt-4" onClick={picker.filters.clear}>
        Quitar filtros
      </Button>
    </div>
  );
}

export function SlotsSection({
  picker,
  updatedAt,
}: {
  picker: SlotPickerState;
  updatedAt: number;
}) {
  const { selection, participants, date, daySchedules } = picker;
  return (
    <section aria-label="Franjas">
      <p role="status" className="empty:hidden">
        {selection.kind === 'lost' && (
          <span className="mb-3 block rounded-xl border-2 border-warning-border bg-warning-bg px-4 py-3 font-semibold text-warning-text">
            {selection.schedule
              ? `La franja de las ${formatLocalTime(selection.schedule.localStartTime)} ya no tiene cupo para ${pluralize(participants, 'persona', 'personas')}. Elige otra.`
              : 'La franja que elegiste ya no está disponible. Elige otra.'}
          </span>
        )}
      </p>
      {date && daySchedules.length > 0 ? (
        <SlotList
          date={date}
          schedules={daySchedules}
          participants={participants}
          selectedId={picker.selectedId}
          onSelect={picker.selectSlot}
        />
      ) : (
        <p className="text-ink-muted">Elige un día con franjas para ver los horarios.</p>
      )}
      <p className="mt-4 text-sm text-ink-muted">
        Los cupos se confirman al reservar. Última consulta: {formatClockTime(updatedAt)}
      </p>
    </section>
  );
}

export function SelectionTotal({
  picker,
  availability,
}: {
  picker: SlotPickerState;
  availability: ExperienceAvailability;
}) {
  return picker.valid ? (
    <p>
      <span className="block text-xl font-bold text-ink tabular-nums">
        {formatCop(picker.total)}
      </span>
      <span className="text-sm text-ink-muted">
        {pluralize(picker.participants, 'persona', 'personas')} ·{' '}
        {formatCop(availability.unitPriceCop)} c/u
      </span>
    </p>
  ) : (
    <p className="font-semibold text-ink-muted">Elige una franja</p>
  );
}

export function ReserveAction({
  picker,
  availability,
  noteId,
  className,
}: {
  picker: SlotPickerState;
  availability: ExperienceAvailability;
  noteId: string;
  className: string;
}) {
  // La selección viaja a la reserva solo como identificadores (ADR-0015 §1).
  return picker.valid ? (
    <Link
      to={`/reservas/nueva?${new URLSearchParams({
        experiencia: availability.experienceId,
        franja: picker.valid.scheduleId,
        personas: String(picker.participants),
      }).toString()}`}
      className={buttonClassName('primary', className)}
    >
      Reservar
    </Link>
  ) : (
    <Button disabled aria-describedby={noteId} className={className}>
      Reservar
    </Button>
  );
}
