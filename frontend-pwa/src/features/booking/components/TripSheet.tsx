import { useCurrentPolicy } from '@/features/auth';
import { useAvailability, useExperience } from '@/features/catalog';
import { formatCop, formatLocalTime, formatLongDate, pluralize } from '@/shared/lib';
import { Icon } from '@/shared/ui';

import type { Booking } from '../api';
import { useMeetingPoint } from '../queries';

/**
 * Ficha de viaje de una reserva confirmada (ADR-0017 §1): lo que el turista necesita el día del
 * recorrido. Sin EPS ni contacto de emergencia.
 */
export function TripSheet({ booking }: { booking: Booking }) {
  const policy = useCurrentPolicy();
  const experience = useExperience(booking.experienceId);
  const availability = useAvailability(booking.experienceId);
  const schedule = availability.data?.schedules.find(
    (item) => item.scheduleId === booking.scheduleId,
  );
  const detail = experience.data;
  // Las paradas solo viajan en la consulta autorizada de la reserva confirmada (ADR-0049).
  const meetingPoint = useMeetingPoint(booking.bookingId, booking.status === 'CONFIRMED');
  const stops = meetingPoint.data?.stops ?? [];

  return (
    <section aria-labelledby="ficha" className="mt-6">
      <h2 id="ficha" className="text-xl font-bold text-ink">
        Ficha de viaje
      </h2>
      {policy.data?.healthNotice && (
        <p className="mt-3 text-sm text-ink-muted">{policy.data.healthNotice.body}</p>
      )}
      <dl className="mt-3 grid gap-3 rounded-xl border-2 border-border-subtle px-4 py-4">
        <div>
          <dt className="text-sm text-ink-muted">Experiencia</dt>
          <dd className="font-semibold text-ink">{detail?.summary.title ?? '…'}</dd>
        </div>
        {schedule && (
          <div>
            <dt className="text-sm text-ink-muted">Fecha y hora</dt>
            <dd className="font-semibold text-ink first-letter:uppercase">
              {formatLongDate(schedule.localDate)} · {formatLocalTime(schedule.localStartTime)} –{' '}
              {formatLocalTime(schedule.localEndTime)}
            </dd>
          </div>
        )}
        {detail && (
          <div>
            <dt className="text-sm text-ink-muted">Punto de encuentro</dt>
            <dd className="font-semibold whitespace-pre-line text-ink">
              {detail.meetingPoint}
              {detail.vereda && (
                <span className="block font-normal text-ink-muted">Vereda {detail.vereda}</span>
              )}
            </dd>
          </div>
        )}
        {detail && (
          <div>
            <dt className="text-sm text-ink-muted">Te recibe</dt>
            <dd className="font-semibold text-ink">{detail.summary.associationName}</dd>
          </div>
        )}
        <div>
          <dt className="text-sm text-ink-muted">Viajeros y valor pagado</dt>
          <dd className="font-semibold text-ink tabular-nums">
            {pluralize(booking.lockedSlots, 'persona', 'personas')} ·{' '}
            {formatCop(booking.totalAmountCop)}
          </dd>
        </div>
      </dl>
      {stops.length > 0 && (
        <section aria-labelledby="paradas" className="mt-4">
          <h3 id="paradas" className="text-lg font-bold text-ink">
            Paradas del recorrido
          </h3>
          <ol className="mt-3 grid gap-3 rounded-xl bg-surface px-4 py-4">
            {stops.map((stop) => (
              <li key={stop.position} className="flex items-start gap-3">
                <span
                  aria-hidden="true"
                  className="flex size-8 shrink-0 items-center justify-center rounded-full bg-brand font-bold text-on-brand"
                >
                  {stop.position}
                </span>
                <div>
                  <p className="font-semibold text-ink">{stop.name}</p>
                  {stop.description && <p className="text-ink-muted">{stop.description}</p>}
                </div>
              </li>
            ))}
          </ol>
        </section>
      )}
      {detail && (
        <details className="mt-3 rounded-xl border-2 border-info-border bg-info-bg px-4 py-3">
          <summary className="flex min-h-11 cursor-pointer items-center gap-2 font-bold text-info-text">
            <Icon name="backpack" size={20} />
            ¿Qué llevar y recomendaciones?
          </summary>
          <p className="mt-2 whitespace-pre-line text-ink">{detail.safetyGuidelines}</p>
        </details>
      )}
    </section>
  );
}
