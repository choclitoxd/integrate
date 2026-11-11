interface OccupancyMeterProps {
  /** Cupos ofrecidos en las próximas jornadas. */
  readonly slots: number;
  /** Cupos con reserva confirmada. */
  readonly booked: number;
}

/**
 * Medidor de un cociente frente a su límite: la pista y el relleno son del mismo tono y el valor va
 * siempre en texto. Sin jornadas próximas no hay cociente: se dice así, no se muestra 0 %.
 */
export function OccupancyMeter({ slots, booked }: OccupancyMeterProps) {
  if (slots === 0) {
    return (
      <p className="rounded-lg bg-surface px-3 py-3 text-ink-muted">
        Sin jornadas próximas. Crea fechas en «Mis experiencias» para ver aquí cuántos cupos van
        reservados.
      </p>
    );
  }
  const percent = Math.min(100, Math.round((booked / slots) * 100));
  return (
    <div className="grid gap-3">
      <p className="text-4xl leading-none font-bold text-brand tabular-nums">{percent} %</p>
      <div
        role="meter"
        aria-label="Ocupación de próximas jornadas"
        aria-valuemin={0}
        aria-valuemax={slots}
        aria-valuenow={Math.min(booked, slots)}
        aria-valuetext={`${String(booked)} de ${String(slots)} cupos reservados`}
        className="h-3 overflow-hidden rounded-full bg-brand-soft"
      >
        <div className="h-full rounded-full bg-brand" style={{ width: `${String(percent)}%` }} />
      </div>
      <p className="text-ink-muted tabular-nums">
        {booked} de {slots} cupos reservados en tus próximas jornadas
      </p>
    </div>
  );
}
