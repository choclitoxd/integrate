import { useId, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router';

import { ApiError } from '@/shared/api';
import { formatClockTime, formatCop, formatLocalTime, pluralize, startOfWeek } from '@/shared/lib';
import { Button, buttonClassName, ProblemNotice, Skeleton, StickyActionBar } from '@/shared/ui';

import type { ExperienceAvailability } from '../api/types';
import { ParticipantStepper } from '../components/ParticipantStepper';
import { SlotList } from '../components/SlotList';
import { WeekPicker } from '../components/WeekPicker';
import { useAvailability } from '../queries';
import {
  firstUsefulDate,
  groupByDate,
  MAX_PARTICIPANTS,
  resolveSelection,
  weekBounds,
} from '../slotSelection';
import { UnavailableExperience } from './ExperienceDetailPage';

function Picker({
  availability,
  updatedAt,
}: {
  availability: ExperienceAvailability;
  updatedAt: number;
}) {
  const noteId = useId();
  const maxParticipants = Math.min(MAX_PARTICIPANTS, availability.maxCapacityPerSlot);
  const [participants, setParticipants] = useState(1);
  const [chosenDate, setChosenDate] = useState<string | null>(null);
  const [chosenWeek, setChosenWeek] = useState<string | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const range = { from: availability.dateFrom, to: availability.dateTo };
  const byDate = useMemo(() => groupByDate(availability.schedules), [availability.schedules]);
  const bounds = weekBounds(range);

  // Sin efectos: la fecha y la semana visibles se derivan mientras el turista no elija otras.
  const date = chosenDate ?? firstUsefulDate(byDate, participants);
  const weekStart = chosenWeek ?? startOfWeek(date ?? range.from);
  const selection = resolveSelection(availability.schedules, selectedId, participants);
  const daySchedules = date ? (byDate.get(date) ?? []) : [];

  if (availability.schedules.length === 0) {
    return (
      <div className="mt-6 rounded-xl bg-surface px-4 py-6 text-center">
        <p className="font-semibold text-ink">
          Por ahora no hay franjas abiertas en los próximos 60 días.
        </p>
        <p className="mt-1 text-ink-muted">
          La comunidad publica nuevas fechas con frecuencia. Vuelve a consultar pronto.
        </p>
        <Link
          to={`/experiencias/${availability.experienceId}`}
          className={buttonClassName('secondary', 'mt-4')}
        >
          Volver a la experiencia
        </Link>
      </div>
    );
  }

  const valid = selection.kind === 'valid' ? selection.schedule : undefined;
  const total = availability.unitPriceCop * participants;

  return (
    <>
      <div className="mt-6 rounded-xl bg-surface px-4 py-4">
        <ParticipantStepper value={participants} max={maxParticipants} onChange={setParticipants} />
        {availability.maxCapacityPerSlot > MAX_PARTICIPANTS && (
          <p className="mt-3 text-sm text-ink-muted">
            Para grupos de más de {MAX_PARTICIPANTS} personas, haz varias reservas.
          </p>
        )}
      </div>

      <section aria-labelledby="fechas" className="mt-6">
        <h2 id="fechas" className="sr-only">
          Fecha
        </h2>
        <WeekPicker
          weekStart={weekStart}
          canGoBack={weekStart > bounds.first}
          canGoForward={weekStart < bounds.last}
          onWeekChange={setChosenWeek}
          selectedDate={date}
          onSelectDate={(next) => {
            setChosenDate(next);
            setSelectedId(null);
          }}
          byDate={byDate}
          participants={participants}
          range={range}
        />
      </section>

      <section aria-label="Franjas" className="mt-6">
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
            selectedId={selectedId}
            onSelect={(scheduleId) => {
              // Fija el día: cambiar luego el número de personas no debe moverlo.
              setChosenDate(date);
              setSelectedId(scheduleId);
            }}
          />
        ) : (
          <p className="text-ink-muted">Elige un día con franjas para ver los horarios.</p>
        )}
        <p className="mt-4 text-sm text-ink-muted">
          Los cupos se confirman al reservar. Última consulta: {formatClockTime(updatedAt)}
        </p>
      </section>

      <StickyActionBar
        summary={
          valid ? (
            <p>
              <span className="block text-xl font-bold text-ink tabular-nums">
                {formatCop(total)}
              </span>
              <span className="text-sm text-ink-muted">
                {pluralize(participants, 'persona', 'personas')} ·{' '}
                {formatCop(availability.unitPriceCop)} c/u
              </span>
            </p>
          ) : (
            <p className="font-semibold text-ink-muted">Elige una franja</p>
          )
        }
        note={
          valid ? undefined : <span id={noteId}>Selecciona día y horario para ver el total.</span>
        }
      >
        {/* La selección viaja a la reserva solo como identificadores (ADR-0015 §1). */}
        {valid ? (
          <Link
            to={`/reservas/nueva?${new URLSearchParams({
              experiencia: availability.experienceId,
              franja: valid.scheduleId,
              personas: String(participants),
            }).toString()}`}
            className={buttonClassName('primary', 'px-6')}
          >
            Reservar
          </Link>
        ) : (
          <Button disabled aria-describedby={noteId} className="px-6">
            Reservar
          </Button>
        )}
      </StickyActionBar>
    </>
  );
}

/** Selector táctil de fecha, franja y personas (T-1.10b). */
export function SlotPickerPage() {
  const { experienceId = '' } = useParams();
  const availability = useAvailability(experienceId);

  if (availability.isError && !availability.data) {
    if (availability.error instanceof ApiError && availability.error.status === 404) {
      return <UnavailableExperience />;
    }
  }

  return (
    <>
      <title>
        {availability.data
          ? `Fechas · ${availability.data.title} · Intégrate`
          : 'Fechas · Intégrate'}
      </title>
      <h1 className="text-3xl leading-tight font-bold text-balance text-brand">
        Elige fecha y franja
      </h1>
      {availability.data && (
        <p className="mt-1 text-lg font-semibold text-ink">{availability.data.title}</p>
      )}

      {availability.isPending && (
        <div aria-busy="true" className="mt-6">
          <p className="sr-only" role="status">
            Consultando cupos…
          </p>
          <Skeleton className="h-20 w-full" />
          <Skeleton className="mt-6 h-24 w-full" />
          <Skeleton className="mt-6 h-14 w-full" />
          <Skeleton className="mt-2 h-14 w-full" />
        </div>
      )}

      {availability.isError && !availability.data && (
        <div className="mt-6">
          <ProblemNotice
            error={availability.error}
            onRetry={() => {
              void availability.refetch();
            }}
          />
        </div>
      )}

      {availability.data && (
        <Picker availability={availability.data} updatedAt={availability.dataUpdatedAt} />
      )}
    </>
  );
}
