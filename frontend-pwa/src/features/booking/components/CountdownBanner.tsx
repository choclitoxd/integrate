import { Icon } from '@/shared/ui';

import { formatCountdown, milestoneOf, type Milestone } from '../countdown';

const ANNOUNCEMENT: Readonly<Record<Milestone, string>> = {
  running: 'Tienes 15 minutos o menos para pagar antes de que se liberen los cupos.',
  five: 'Quedan 5 minutos para pagar.',
  one: 'Queda 1 minuto para pagar.',
  over: 'El tiempo para pagar terminó.',
};

/**
 * Cuenta regresiva del TTL (ADR-0016 §2). El reloj visible cambia cada segundo; el lector de
 * pantalla solo oye los hitos, para no interrumpir mientras se elige el método de pago.
 */
export function CountdownBanner({ remaining }: { remaining: number }) {
  const milestone = milestoneOf(remaining);
  const urgent = milestone === 'one' || milestone === 'over';
  return (
    <div
      className={`mt-4 rounded-xl border-2 px-4 py-3 ${
        urgent
          ? 'border-warning-border bg-warning-bg text-warning-text'
          : 'border-success-border bg-success-bg text-success-text'
      }`}
    >
      <p className="flex items-center gap-3">
        <Icon name="clock" size={24} />
        <span className="font-semibold">
          {milestone === 'over' ? 'El tiempo terminó, verificando…' : 'Tienes para pagar'}
        </span>
        {milestone !== 'over' && (
          <span
            role="timer"
            aria-label={`${formatCountdown(remaining)} minutos restantes`}
            className="ml-auto text-2xl font-bold tabular-nums"
          >
            {formatCountdown(remaining)}
          </span>
        )}
      </p>
      {milestone === 'one' && (
        <p className="mt-1 text-sm font-semibold">
          Último minuto: si no pagas, los cupos se liberan.
        </p>
      )}
      <p aria-live="polite" className="sr-only">
        {ANNOUNCEMENT[milestone]}
      </p>
    </div>
  );
}
