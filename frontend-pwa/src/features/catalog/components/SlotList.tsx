import { formatLocalTime, formatLongDate } from '@/shared/lib';

import type { ScheduleAvailability } from '../api/types';
import { fits } from '../slotSelection';

interface SlotListProps {
  readonly date: string;
  readonly schedules: readonly ScheduleAvailability[];
  readonly participants: number;
  readonly selectedId: string | null;
  readonly onSelect: (scheduleId: string) => void;
}

const LOW_STOCK = 3;

function availabilityText(schedule: ScheduleAvailability, participants: number): string {
  const { availableSlots, totalSlots } = schedule;
  if (availableSlots === 0) return 'Agotada';
  if (availableSlots < participants) {
    return availableSlots === 1
      ? 'Solo queda 1 cupo'
      : `Solo quedan ${String(availableSlots)} cupos`;
  }
  if (availableSlots <= LOW_STOCK) {
    return availableSlots === 1 ? 'Último cupo' : `Últimos ${String(availableSlots)} cupos`;
  }
  return `Quedan ${String(availableSlots)} de ${String(totalSlots)} cupos`;
}

/**
 * Franjas del día como grupo de radio nativo (T-1.10b). Una franja que no alcanza para el grupo
 * queda inhabilitada y lo dice en texto, no solo con color.
 */
export function SlotList({ date, schedules, participants, selectedId, onSelect }: SlotListProps) {
  return (
    <fieldset>
      <legend className="text-lg font-bold text-ink first-letter:uppercase">
        Franjas del {formatLongDate(date)}
      </legend>
      <div className="mt-3 grid gap-2">
        {schedules.map((schedule) => {
          const usable = fits(schedule, participants);
          const lowStock = usable && schedule.availableSlots <= LOW_STOCK;
          return (
            <label
              key={schedule.scheduleId}
              className={`flex min-h-14 flex-wrap items-center gap-x-3 gap-y-0.5 rounded-xl border-2 px-4 py-3 ${
                usable
                  ? 'cursor-pointer border-border bg-canvas hover:bg-brand-soft has-checked:border-brand has-checked:bg-brand-soft'
                  : 'cursor-not-allowed border-border-subtle bg-surface-subtle'
              }`}
            >
              <input
                type="radio"
                name="franja"
                value={schedule.scheduleId}
                checked={usable && selectedId === schedule.scheduleId}
                disabled={!usable}
                onChange={() => {
                  onSelect(schedule.scheduleId);
                }}
                className="size-5 shrink-0 accent-brand"
              />
              <span
                className={`flex-1 font-semibold whitespace-nowrap tabular-nums ${usable ? 'text-ink' : 'text-ink-muted line-through decoration-2'}`}
              >
                {formatLocalTime(schedule.localStartTime)} –{' '}
                {formatLocalTime(schedule.localEndTime)}
              </span>
              <span
                className={`ml-8 text-sm font-semibold min-[400px]:ml-0 ${
                  !usable ? 'text-danger-text' : lowStock ? 'text-warning-text' : 'text-ink-muted'
                }`}
              >
                {availabilityText(schedule, participants)}
              </span>
            </label>
          );
        })}
      </div>
    </fieldset>
  );
}
