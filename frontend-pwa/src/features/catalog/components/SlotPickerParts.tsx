import { Link } from 'react-router';

import { formatClockTime, formatCop, formatLocalTime, pluralize } from '@/shared/lib';
import { Button, buttonClassName } from '@/shared/ui';

import type { ExperienceAvailability } from '../api/types';
import { MAX_PARTICIPANTS } from '../slotSelection';
import type { SlotPickerState } from '../useSlotPicker';
import { ParticipantStepper } from './ParticipantStepper';
import { SlotList } from './SlotList';
import { WeekPicker } from './WeekPicker';

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

export function DateSection({ picker }: { picker: SlotPickerState }) {
  return (
    <section aria-labelledby="fechas">
      <h2 id="fechas" className="sr-only">
        Fecha
      </h2>
      <WeekPicker
        {...picker.week}
        selectedDate={picker.date}
        onSelectDate={picker.selectDate}
        byDate={picker.byDate}
        participants={picker.participants}
        range={picker.range}
      />
    </section>
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
