import { useId, type ReactNode } from 'react';
import { useParams } from 'react-router';

import { ApiError } from '@/shared/api';
import { ProblemNotice, Skeleton } from '@/shared/ui';

import type { ExperienceAvailability } from '../api/types';
import {
  DateSection,
  NoFilterMatches,
  NoSchedules,
  ParticipantsBox,
  ReserveAction,
  SelectionTotal,
  SlotFiltersBar,
  SlotsSection,
} from '../components/SlotPickerParts';
import { useAvailability } from '../queries';
import { useSlotPicker } from '../useSlotPicker';
import { UnavailableExperience } from './ExperienceDetailPage';

function Picker({
  availability,
  updatedAt,
}: {
  availability: ExperienceAvailability;
  updatedAt: number;
}) {
  const noteId = useId();
  const picker = useSlotPicker(availability);

  if (availability.schedules.length === 0) {
    return <NoSchedules experienceId={availability.experienceId} />;
  }

  return (
    <>
      <div className="mt-6">
        <ParticipantsBox picker={picker} availability={availability} />
      </div>
      <div className="mt-6">
        <SlotFiltersBar picker={picker} />
      </div>
      {picker.filters.noMatches ? (
        <div className="mt-6">
          <NoFilterMatches picker={picker} />
        </div>
      ) : (
        <>
          <div className="mt-4">
            <DateSection picker={picker} bleed />
          </div>
          <div className="mt-6">
            <SlotsSection picker={picker} updatedAt={updatedAt} />
          </div>

          {/* Acción justo bajo las franjas, con el total encima: no en una barra fija al fondo. */}
          <div className="mt-6 grid gap-3 rounded-xl bg-surface px-4 py-4">
            <SelectionTotal picker={picker} availability={availability} />
            <ReserveAction
              picker={picker}
              availability={availability}
              noteId={noteId}
              className="w-full"
            />
            {!picker.valid && (
              <p id={noteId} className="text-sm text-ink-muted">
                Selecciona día y horario para ver el total.
              </p>
            )}
          </div>
        </>
      )}
    </>
  );
}

/**
 * Título, carga, 404 y error del selector, comunes a la vista móvil y a la de escritorio
 * (ADR-0020 §6). `children` recibe la disponibilidad ya cargada.
 */
export function SlotPickerLoader({
  children,
}: {
  children: (availability: ExperienceAvailability, updatedAt: number) => ReactNode;
}) {
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

      {availability.data && children(availability.data, availability.dataUpdatedAt)}
    </>
  );
}

/** Selector táctil de fecha, franja y personas (T-1.10b). */
export function SlotPickerPage() {
  return (
    <SlotPickerLoader>
      {(availability, updatedAt) => <Picker availability={availability} updatedAt={updatedAt} />}
    </SlotPickerLoader>
  );
}
