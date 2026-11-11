import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type SubmitEvent } from 'react';

import type { AdminExperience } from '@/features/admin';
import { useHttpClient } from '@/shared/api';
import { formatDateTime } from '@/shared/lib';
import { Button, ProblemNotice, TextField } from '@/shared/ui';

interface Schedule {
  readonly id: string;
  readonly startTime: string;
  readonly endTime: string;
  readonly totalSlots: number;
  readonly availableSlots: number;
  readonly status: 'OPEN' | 'CLOSED' | 'CANCELLED';
  readonly cancellationReason: string | null;
}

function bogotaDate(date: Date) {
  return new Intl.DateTimeFormat('sv-SE', { timeZone: 'America/Bogota' }).format(date);
}

export function HostSchedules({ experience }: { readonly experience: AdminExperience }) {
  const http = useHttpClient();
  const client = useQueryClient();
  const [openedAt] = useState(Date.now);
  const [from, setFrom] = useState(() => bogotaDate(new Date()));
  const [to, setTo] = useState(() => bogotaDate(new Date(Date.now() + 30 * 86400000)));
  const [cancelId, setCancelId] = useState<string | null>(null);
  const [notice, setNotice] = useState('');
  const base = `/v1/host/experiences/${encodeURIComponent(experience.id)}/schedules`;
  const key = ['me', 'host', 'schedules', experience.id];
  const validRange = Boolean(from && to && from <= to);
  const list = useQuery({
    queryKey: [...key, from, to],
    enabled: validRange,
    queryFn: ({ signal }) =>
      http.request<Schedule[]>(`${base}?dateFrom=${from}&dateTo=${to}`, { signal }),
  });
  const mutation = useMutation({
    mutationFn: ({ path, body }: { path: string; body: object }) =>
      http.request(path, { method: 'POST', body }),
    onSuccess: (_, variables) => {
      setNotice(variables.path === base ? 'Horario creado.' : 'Horario cancelado.');
      setCancelId(null);
      void client.invalidateQueries({ queryKey: key });
    },
  });
  const canAdd = ['DRAFT', 'PENDING_REVIEW', 'REJECTED', 'APPROVED'].includes(experience.status);

  function add(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const localStart = form.get('localStart');
    const localEnd = form.get('localEnd');
    if (typeof localStart !== 'string' || typeof localEnd !== 'string') return;
    if (localEnd <= localStart) {
      setNotice('La hora de fin debe ser posterior a la de inicio.');
      return;
    }
    setNotice('');
    mutation.mutate({
      path: base,
      body: { schedules: [{ localStart, localEnd, totalSlots: Number(form.get('totalSlots')) }] },
    });
  }

  return (
    <section
      aria-labelledby="horarios-anfitrion"
      className="grid gap-4 border-t-2 border-border-subtle pt-4"
    >
      <h2 id="horarios-anfitrion" className="text-xl font-bold">
        Horarios
      </h2>
      <div className="grid gap-2">
        <TextField
          label="Desde"
          type="date"
          value={from}
          onChange={(event) => {
            setFrom(event.target.value);
          }}
        />
        <TextField
          label="Hasta"
          type="date"
          value={to}
          onChange={(event) => {
            setTo(event.target.value);
          }}
        />
      </div>
      {!validRange ? (
        <p role="status">Selecciona un rango de fechas válido.</p>
      ) : list.isError ? (
        <ProblemNotice error={list.error} onRetry={() => void list.refetch()} />
      ) : list.isPending ? (
        <p role="status">Cargando horarios…</p>
      ) : list.data.length === 0 ? (
        <p>No hay horarios en estas fechas.</p>
      ) : (
        <ul className="grid gap-3">
          {list.data.map((slot) => (
            <li key={slot.id} className="grid gap-2 rounded-xl border-2 border-border-subtle p-4">
              <p className="font-bold">{formatDateTime(Date.parse(slot.startTime))}</p>
              <p>Termina: {formatDateTime(Date.parse(slot.endTime))}</p>
              <p>
                {slot.availableSlots} de {slot.totalSlots} cupos disponibles ·{' '}
                {slot.status === 'CANCELLED'
                  ? 'Cancelado'
                  : slot.status === 'OPEN'
                    ? 'Abierto'
                    : 'Cerrado'}
              </p>
              {slot.cancellationReason && <p className="break-words">{slot.cancellationReason}</p>}
              {slot.status !== 'CANCELLED' &&
                Date.parse(slot.startTime) > openedAt &&
                slot.availableSlots === slot.totalSlots &&
                (cancelId === slot.id ? (
                  <form
                    className="grid gap-2"
                    onSubmit={(event) => {
                      event.preventDefault();
                      const input = new FormData(event.currentTarget).get('reason');
                      if (typeof input !== 'string' || !input.trim()) return;
                      const reason = input.trim();
                      setNotice('');
                      mutation.mutate({
                        path: `/v1/host/schedules/${encodeURIComponent(slot.id)}/cancel`,
                        body: { reason },
                      });
                    }}
                  >
                    <TextField
                      label="Motivo de cancelación"
                      name="reason"
                      required
                      maxLength={1000}
                    />
                    <Button type="submit" disabled={mutation.isPending}>
                      Confirmar cancelación
                    </Button>
                    <Button
                      type="button"
                      variant="secondary"
                      disabled={mutation.isPending}
                      onClick={() => {
                        setCancelId(null);
                        mutation.reset();
                      }}
                    >
                      Conservar horario
                    </Button>
                  </form>
                ) : (
                  <Button
                    variant="secondary"
                    disabled={mutation.isPending}
                    onClick={() => {
                      setCancelId(slot.id);
                      mutation.reset();
                    }}
                  >
                    Cancelar horario
                  </Button>
                ))}
            </li>
          ))}
        </ul>
      )}
      {canAdd && (
        <form className="grid gap-3" onSubmit={add}>
          <h3 className="font-bold">Añadir horario</h3>
          <p className="text-ink-muted">Usa la hora de Colombia.</p>
          <TextField label="Inicio" type="datetime-local" name="localStart" required />
          <TextField label="Fin" type="datetime-local" name="localEnd" required />
          <TextField
            label="Cupos"
            type="number"
            name="totalSlots"
            required
            min={1}
            max={experience.maxCapacityPerSlot}
          />
          <Button type="submit" disabled={mutation.isPending}>
            Guardar horario
          </Button>
        </form>
      )}
      {notice && <p role="status">{notice}</p>}
      {mutation.isError && <ProblemNotice error={mutation.error} />}
    </section>
  );
}
