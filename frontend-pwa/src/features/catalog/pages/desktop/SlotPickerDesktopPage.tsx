import { useId } from 'react';

import { formatLongDate, formatLocalTime } from '@/shared/lib';

import type { ExperienceAvailability } from '../../api/types';
import {
  DateSection,
  NoSchedules,
  ParticipantsBox,
  ReserveAction,
  SelectionTotal,
  SlotsSection,
} from '../../components/SlotPickerParts';
import { useSlotPicker } from '../../useSlotPicker';
import { SlotPickerLoader } from '../SlotPickerPage';

function DesktopPicker({
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
    <div className="mt-8 grid grid-cols-[minmax(0,1fr)_19rem] items-start gap-12">
      <div className="grid gap-8">
        <div className="max-w-md">
          <ParticipantsBox picker={picker} availability={availability} />
        </div>
        {/* La semana necesita 356 px para sus 7 columnas (WeekPicker); las franjas toman el resto. */}
        <div className="grid grid-cols-[minmax(22.5rem,1fr)_minmax(0,1fr)] items-start gap-8">
          <DateSection picker={picker} />
          <SlotsSection picker={picker} updatedAt={updatedAt} />
        </div>
      </div>

      <aside
        aria-labelledby="resumen-titulo"
        className="sticky top-24 rounded-xl border-2 border-border-subtle bg-surface-elevated p-6"
      >
        <h2 id="resumen-titulo" className="text-lg font-bold text-ink">
          Tu selección
        </h2>
        {picker.valid && (
          <p className="mt-2 text-ink first-letter:uppercase">
            {formatLongDate(picker.valid.localDate)} ·{' '}
            {formatLocalTime(picker.valid.localStartTime)} –{' '}
            {formatLocalTime(picker.valid.localEndTime)}
          </p>
        )}
        <div className="mt-4 border-t border-border-subtle pt-4">
          <SelectionTotal picker={picker} availability={availability} />
        </div>
        <ReserveAction
          picker={picker}
          availability={availability}
          noteId={noteId}
          className="mt-5 w-full"
        />
        {!picker.valid && (
          <p id={noteId} className="mt-2 text-sm text-ink-muted">
            Selecciona día y horario para ver el total.
          </p>
        )}
      </aside>
    </div>
  );
}

/** Selector de franjas en escritorio (ADR-0020 §7): semana y franjas lado a lado, total al costado. */
export function SlotPickerDesktopPage() {
  return (
    <SlotPickerLoader>
      {(availability, updatedAt) => (
        <DesktopPicker availability={availability} updatedAt={updatedAt} />
      )}
    </SlotPickerLoader>
  );
}
