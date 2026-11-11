import { useEffect, useState, type ReactNode } from 'react';
import { Link } from 'react-router';

import { ApiError, useHttpClient } from '@/shared/api';
import {
  addDays,
  BOGOTA_TIME_ZONE,
  formatClockTime,
  formatDateTime,
  todayInBogota,
} from '@/shared/lib';
import { useOnlineStatus } from '@/shared/offline';
import {
  BottomSheet,
  Button,
  buttonClassName,
  DatePickerField,
  Icon,
  ProblemNotice,
  Select,
  Skeleton,
  TextField,
  type IconName,
} from '@/shared/ui';

interface HostBooking {
  readonly bookingId: string;
  readonly experienceTitle: string;
  readonly startsAt: string;
  readonly holderName: string;
  readonly scheduleId: string;
  readonly slotsCount: number;
  readonly scheduleBookings: number;
  readonly scheduleSlots: number;
}
interface BookingPage {
  readonly items: readonly HostBooking[];
  readonly page: number;
  readonly totalPages: number;
  readonly totalElements?: number;
}
type EpsState =
  | { status: 'loading' }
  | { status: 'success'; value: string | null }
  | { status: 'error'; error: unknown };

const PERIOD_OPTIONS = [
  { value: 'ALL', label: 'Todas' },
  { value: 'UPCOMING', label: 'Próximas' },
  { value: 'HISTORY', label: 'Historial' },
] as const;

const dayFormat = new Intl.DateTimeFormat('es-CO', { day: 'numeric', timeZone: BOGOTA_TIME_ZONE });
const monthFormat = new Intl.DateTimeFormat('es-CO', {
  month: 'short',
  timeZone: BOGOTA_TIME_ZONE,
});
const nextFormat = new Intl.DateTimeFormat('es-CO', {
  weekday: 'long',
  day: 'numeric',
  month: 'long',
  hour: 'numeric',
  minute: '2-digit',
  timeZone: BOGOTA_TIME_ZONE,
});

const CHIP_CLASS =
  'inline-flex min-h-11 items-center rounded-full border-2 border-border bg-canvas px-4 font-semibold text-ink hover:bg-surface-subtle';

function SummaryTile({
  icon,
  label,
  children,
}: {
  icon: IconName;
  label: string;
  children: ReactNode;
}) {
  return (
    <div className="flex items-center gap-3 rounded-xl border-2 border-border-subtle p-4">
      <span className="grid size-11 shrink-0 place-items-center rounded-full bg-brand-soft text-brand">
        <Icon name={icon} size={22} />
      </span>
      <div className="min-w-0">
        <dt className="text-sm font-semibold text-ink-muted">{label}</dt>
        <dd className="text-lg font-bold break-words text-ink">{children}</dd>
      </div>
    </div>
  );
}

/** Reservas confirmadas de las experiencias del anfitrión, con resumen, filtros y consulta de EPS. */
export function HostBookingsPage() {
  const http = useHttpClient();
  const online = useOnlineStatus();
  const [filters, setFilters] = useState('');
  const [period, setPeriod] = useState('ALL');
  const [date, setDate] = useState('');
  const [experience, setExperience] = useState('');
  const [page, setPage] = useState(0);
  const [list, setList] = useState<BookingPage | null>(null);
  const [listError, setListError] = useState<unknown>(null);
  const [selected, setSelected] = useState<HostBooking | null>(null);
  const [eps, setEps] = useState<EpsState>({ status: 'loading' });
  const [reload, setReload] = useState(0);
  // Referencia fija para «próxima jornada»: no cambia en cada render.
  const [now] = useState(() => Date.now());

  useEffect(() => {
    if (!online) return;
    const controller = new AbortController();
    void http
      .request<BookingPage>(`/v1/host/bookings?page=${String(page)}&size=20${filters}`, {
        signal: controller.signal,
      })
      .then((data) => {
        if (!controller.signal.aborted) {
          setList(data);
          setListError(null);
        }
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) setListError(error);
      });
    return () => {
      controller.abort();
    };
  }, [http, page, reload, online, filters]);

  useEffect(() => {
    if (!selected || !online) return;
    const controller = new AbortController();
    void http
      .request<{ medicalEps: string | null }>(
        `/v1/host/bookings/${encodeURIComponent(selected.bookingId)}/holder-eps`,
        { signal: controller.signal },
      )
      .then((data) => {
        if (!controller.signal.aborted) setEps({ status: 'success', value: data.medicalEps });
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) setEps({ status: 'error', error });
      });
    return () => {
      controller.abort();
    };
  }, [http, selected, online]);

  const close = () => {
    setSelected(null);
    setEps({ status: 'loading' });
  };

  function applyFilters(next: string) {
    if (next === filters && page === 0) return;
    setList(null);
    setListError(null);
    setPage(0);
    setFilters(next);
  }
  function currentFilters() {
    const params = new URLSearchParams();
    if (period !== 'ALL') params.set('period', period);
    if (date.trim()) params.set('date', date.trim());
    if (experience.trim()) params.set('experience', experience.trim());
    return params.size > 0 ? `&${params.toString()}` : '';
  }
  function clearFilters() {
    setPeriod('ALL');
    setDate('');
    setExperience('');
    applyFilters('');
  }

  const items = list?.items ?? [];
  const people = items.reduce((sum, booking) => sum + booking.slotsCount, 0);
  const nextStart = items
    .map((booking) => Date.parse(booking.startsAt))
    .filter((time) => time >= now)
    .sort((a, b) => a - b)[0];
  const filtered = filters !== '';

  return (
    <>
      <title>Reservas confirmadas · Intégrate</title>
      <h1 className="text-3xl font-bold text-brand">Reservas confirmadas</h1>
      <p className="mt-3 max-w-prose text-ink-muted">
        Consulta las reservas de tus experiencias. La EPS del titular solo se consulta
        individualmente para atender una emergencia.
      </p>

      {list && (
        <dl aria-label="Resumen de reservas" className="mt-6 grid gap-3 sm:grid-cols-3">
          <SummaryTile icon="ticket" label="Reservas">
            {list.totalElements ?? items.length}
          </SummaryTile>
          <SummaryTile icon="users" label="Personas en esta lista">
            {people}
          </SummaryTile>
          <SummaryTile icon="calendar" label="Próxima jornada">
            {nextStart === undefined ? 'Sin jornadas próximas' : nextFormat.format(nextStart)}
          </SummaryTile>
        </dl>
      )}

      <section aria-labelledby="filtros-reservas" className="mt-6 rounded-xl bg-surface p-4">
        <h2 id="filtros-reservas" className="flex items-center gap-2 text-lg font-bold text-ink">
          <Icon name="filters" size={20} className="text-brand" />
          Filtrar reservas
        </h2>
        <form
          className="mt-4 grid gap-4 md:grid-cols-3"
          onSubmit={(event) => {
            event.preventDefault();
            applyFilters(currentFilters());
          }}
        >
          <Select
            label="Jornada"
            name="period"
            value={period}
            options={PERIOD_OPTIONS}
            onChange={setPeriod}
          />
          <div>
            <DatePickerField
              name="date"
              label="Fecha de la jornada"
              value={date}
              onChange={setDate}
            />
            <div role="group" aria-label="Fechas rápidas" className="mt-2 flex flex-wrap gap-2">
              <button
                type="button"
                className={CHIP_CLASS}
                onClick={() => {
                  setDate(todayInBogota());
                }}
              >
                Hoy
              </button>
              <button
                type="button"
                className={CHIP_CLASS}
                onClick={() => {
                  setDate(addDays(todayInBogota(), 1));
                }}
              >
                Mañana
              </button>
              {date !== '' && (
                <button
                  type="button"
                  className={CHIP_CLASS}
                  onClick={() => {
                    setDate('');
                  }}
                >
                  Quitar fecha
                </button>
              )}
            </div>
          </div>
          <TextField
            name="experience"
            label="Nombre de la experiencia"
            maxLength={200}
            value={experience}
            onChange={(event) => {
              setExperience(event.target.value);
            }}
          />
          <div className="flex flex-wrap gap-3 md:col-span-3">
            <Button type="submit" disabled={!online}>
              <Icon name="search" />
              Aplicar filtros
            </Button>
            {(filtered || period !== 'ALL' || date !== '' || experience !== '') && (
              <Button type="button" variant="secondary" onClick={clearFilters}>
                Limpiar filtros
              </Button>
            )}
          </div>
        </form>
      </section>

      <div className="mt-6" aria-live="polite">
        {!online ? (
          <p role="status">Sin conexión. Conéctate para consultar las reservas actualizadas.</p>
        ) : listError ? (
          <ProblemNotice
            error={listError}
            onRetry={() => {
              setReload((n) => n + 1);
            }}
          />
        ) : !list ? (
          <div className="grid gap-4 md:grid-cols-2">
            <span className="sr-only">Cargando reservas…</span>
            <Skeleton className="h-40 w-full" />
            <Skeleton className="h-40 w-full" />
          </div>
        ) : list.items.length === 0 ? (
          <div className="grid justify-items-center gap-3 rounded-xl border-2 border-dashed border-border-subtle px-4 py-10 text-center">
            <span className="grid size-14 place-items-center rounded-full bg-brand-soft text-brand">
              <Icon name="calendar" size={28} />
            </span>
            <p className="text-lg font-bold text-ink">
              {filtered
                ? 'Ninguna reserva coincide con tus filtros.'
                : 'No tienes reservas confirmadas para mostrar.'}
            </p>
            <p className="max-w-prose text-ink-muted">
              {filtered
                ? 'Prueba con otra fecha o quita algún filtro.'
                : 'Cuando alguien reserve una de tus experiencias, aparecerá aquí con sus datos.'}
            </p>
            {filtered ? (
              <Button variant="secondary" onClick={clearFilters}>
                Limpiar filtros
              </Button>
            ) : (
              <Link to="/anfitrion/experiencias" className={buttonClassName('secondary')}>
                Ver mis experiencias
              </Link>
            )}
          </div>
        ) : (
          <ul className="grid gap-4 md:grid-cols-2">
            {list.items.map((booking, index) => {
              const start = Date.parse(booking.startsAt);
              return (
                <li
                  key={booking.bookingId}
                  className="flex min-w-0 gap-4 rounded-xl border-2 border-border-subtle p-4"
                >
                  <div
                    aria-hidden="true"
                    className="grid size-16 shrink-0 content-center justify-items-center rounded-xl bg-brand text-on-brand"
                  >
                    <span className="text-2xl leading-none font-bold">
                      {dayFormat.format(start)}
                    </span>
                    <span className="text-xs font-semibold tracking-wide uppercase">
                      {monthFormat.format(start).replace('.', '')}
                    </span>
                  </div>
                  <div className="min-w-0 flex-1">
                    <h2 className="text-lg font-bold break-words text-ink">
                      {booking.experienceTitle}
                    </h2>
                    <p className="mt-1 flex items-start gap-1.5 text-ink-muted">
                      <Icon name="clock" size={18} className="mt-0.5 shrink-0" />
                      <span>
                        {formatDateTime(start)}
                        <span className="sr-only"> ({formatClockTime(start)})</span>
                      </span>
                    </p>
                    {booking.scheduleId &&
                      booking.scheduleId !== list.items[index - 1]?.scheduleId && (
                        <p className="mt-1 text-ink-muted">
                          Jornada: {booking.scheduleBookings} reservas · {booking.scheduleSlots}{' '}
                          cupos reservados
                        </p>
                      )}
                    <p className="mt-1 text-ink">Titular: {booking.holderName}</p>
                    {booking.slotsCount > 0 && (
                      <p className="mt-1 inline-flex items-center gap-1.5 text-ink">
                        <Icon name="users" size={18} className="text-brand" />
                        Personas en la reserva: {booking.slotsCount}
                      </p>
                    )}
                    <Button
                      variant="secondary"
                      className="mt-4 w-full"
                      onClick={() => {
                        setEps({ status: 'loading' });
                        setSelected(booking);
                      }}
                    >
                      Consultar EPS en emergencia
                      <span className="sr-only">
                        {' '}
                        de {booking.holderName}, {booking.experienceTitle}
                      </span>
                    </Button>
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </div>
      {online && list && list.totalPages > 1 && (
        <nav
          aria-label="Páginas de reservas"
          className="mt-6 flex items-center justify-between gap-2"
        >
          <Button
            variant="secondary"
            disabled={page === 0}
            onClick={() => {
              setList(null);
              setPage(page - 1);
            }}
          >
            Anterior
          </Button>
          <span>
            Página {page + 1} de {list.totalPages}
          </span>
          <Button
            variant="secondary"
            disabled={page + 1 >= list.totalPages}
            onClick={() => {
              setList(null);
              setPage(page + 1);
            }}
          >
            Siguiente
          </Button>
        </nav>
      )}
      <BottomSheet open={online && selected !== null} onClose={close} title="EPS del titular">
        {selected && (
          <p className="mb-3 font-semibold">
            {selected.holderName} · {selected.experienceTitle}
          </p>
        )}
        <p className="mb-3 text-ink-muted">
          Consulta esta información solo para atender una emergencia.
        </p>
        {eps.status === 'loading' ? (
          <p role="status">Consultando…</p>
        ) : eps.status === 'success' ? (
          <p>{eps.value ?? 'El titular no proporcionó una EPS.'}</p>
        ) : eps.error instanceof ApiError && eps.error.status === 403 ? (
          <div role="alert">
            <p>No es posible consultar la EPS. La autorización requerida no está disponible.</p>
            {eps.error.traceId && <p>Código de seguimiento: {eps.error.traceId}</p>}
          </div>
        ) : (
          <ProblemNotice error={eps.error} />
        )}
      </BottomSheet>
    </>
  );
}
