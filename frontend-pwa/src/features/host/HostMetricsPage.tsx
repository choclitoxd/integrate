import { useQuery } from '@tanstack/react-query';
import { useState, type ReactNode } from 'react';

import { useHttpClient } from '@/shared/api';
import { formatDateTime, formatLongDate, pluralize } from '@/shared/lib';
import { useOnlineStatus } from '@/shared/offline';
import { Button, Icon, ProblemNotice, Select, Skeleton, type IconName } from '@/shared/ui';

import { ColumnChart, DataTable, type ColumnPoint, type TableRow } from './charts/ColumnChart';
import { OccupancyMeter } from './charts/OccupancyMeter';
import { RankedBars, RankedTable, type RankedItem } from './charts/RankedBars';

interface Metrics {
  readonly month: string;
  readonly currentMonth: string;
  readonly earliestMonth: string;
  readonly updatedAt: string;
  readonly reservations: number;
  readonly people: number;
  readonly upcoming: number;
  readonly upcomingPeople: number;
  readonly ranking: readonly {
    experienceId: string;
    title: string;
    reservations: number;
    people: number;
  }[];
  readonly daily: readonly { date: string; reservations: number; people: number }[];
  readonly monthly: readonly { month: string; reservations: number; people: number }[];
  readonly occupancy: { readonly slots: number; readonly booked: number };
}

const monthFormat = new Intl.DateTimeFormat('es-CO', {
  year: 'numeric',
  month: 'long',
  timeZone: 'UTC',
});
const shortMonthFormat = new Intl.DateTimeFormat('es-CO', { month: 'short', timeZone: 'UTC' });
const TICK_DAYS = new Set([1, 8, 15, 22, 29]);

function monthsBetween(first: string, last: string) {
  const cursor = new Date(`${last}-01T00:00:00Z`);
  const options: { value: string; label: string }[] = [];
  while (options.length < 12 && Number.isFinite(cursor.getTime())) {
    const value = cursor.toISOString().slice(0, 7);
    if (value < first) break;
    options.push({ value, label: monthFormat.format(cursor) });
    cursor.setUTCMonth(cursor.getUTCMonth() - 1);
  }
  return options;
}

const reservationsText = (count: number) => pluralize(count, 'reserva', 'reservas');
const peopleText = (count: number) => pluralize(count, 'persona', 'personas');

/** Todos los días del mes (los vacíos en cero) para que la columna de cada día tenga su sitio. */
function dailyPoints(data: Metrics): ColumnPoint[] {
  const [year = 0, month = 1] = data.month.split('-').map(Number);
  const days = new Date(Date.UTC(year, month, 0)).getUTCDate();
  const byDate = new Map(data.daily.map((point) => [point.date, point]));
  return Array.from({ length: days }, (_, index) => {
    const day = index + 1;
    const date = `${data.month}-${String(day).padStart(2, '0')}`;
    const found = byDate.get(date);
    const reservations = found?.reservations ?? 0;
    const people = found?.people ?? 0;
    return {
      key: date,
      tick: TICK_DAYS.has(day) ? String(day) : null,
      value: reservations,
      detail: `${formatLongDate(date)}: ${reservationsText(reservations)} · ${peopleText(people)}`,
    };
  });
}

function monthlyPoints(data: Metrics): ColumnPoint[] {
  return data.monthly.map((point) => {
    const date = new Date(`${point.month}-01T00:00:00Z`);
    return {
      key: point.month,
      tick: shortMonthFormat.format(date).replace('.', ''),
      value: point.reservations,
      detail: `${monthFormat.format(date)}: ${reservationsText(point.reservations)} · ${peopleText(point.people)}`,
      highlight: point.month === data.month,
    };
  });
}

function summarize(what: string, points: readonly ColumnPoint[], label: (key: string) => string) {
  const total = points.reduce((sum, point) => sum + point.value, 0);
  if (total === 0) return `${what}: sin reservas.`;
  const peak = points.reduce((best, point) => (point.value > best.value ? point : best));
  return `${what}: ${reservationsText(total)}. El mayor valor fue ${label(peak.key)}, con ${reservationsText(peak.value)}.`;
}

function Tile({ icon, label, value }: { icon: IconName; label: string; value: number }) {
  return (
    <div className="flex items-center gap-3 rounded-xl border-2 border-border-subtle p-4">
      <span className="grid size-12 shrink-0 place-items-center rounded-full bg-brand-soft text-brand">
        <Icon name={icon} size={24} />
      </span>
      <div className="min-w-0">
        <dt className="text-sm font-semibold text-ink-muted">{label}</dt>
        <dd className="text-3xl leading-tight font-bold text-brand tabular-nums">{value}</dd>
      </div>
    </div>
  );
}

/** Tarjeta de una gráfica con su alternativa en tabla: el mismo dato, dos formas de leerlo. */
function ChartCard({
  id,
  title,
  description,
  chart,
  table,
  className = '',
}: {
  id: string;
  title: string;
  description?: string;
  chart: ReactNode;
  table?: ReactNode;
  className?: string;
}) {
  const [asTable, setAsTable] = useState(false);
  return (
    <section
      aria-labelledby={id}
      className={`min-w-0 rounded-xl border-2 border-border-subtle p-4 ${className}`}
    >
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <h2 id={id} className="text-xl font-bold text-ink">
            {title}
          </h2>
          {description && <p className="mt-1 text-sm text-ink-muted">{description}</p>}
        </div>
        {table && (
          <Button
            variant="secondary"
            aria-pressed={asTable}
            onClick={() => {
              setAsTable((current) => !current);
            }}
          >
            {asTable ? 'Ver gráfica' : 'Ver como tabla'}
          </Button>
        )}
      </div>
      <div className="mt-4">{asTable && table ? table : chart}</div>
    </section>
  );
}

export function HostMetricsPage() {
  const http = useHttpClient();
  const online = useOnlineStatus();
  const [month, setMonth] = useState('');
  const query = useQuery({
    queryKey: ['me', 'host', 'metrics', month],
    queryFn: ({ signal }) =>
      http.request<Metrics>(
        `/v1/host/metrics${month ? `?month=${encodeURIComponent(month)}` : ''}`,
        { signal },
      ),
    enabled: online,
    placeholderData: (previous) => previous,
    gcTime: 0,
  });
  const data = query.data;

  return (
    <>
      <title>Mis métricas · Intégrate</title>
      <h1 className="text-3xl font-bold text-brand">Mis métricas</h1>
      <p className="mt-2 max-w-prose text-ink-muted">
        Consulta las reservas de tus experiencias por mes de la jornada.
      </p>
      <div className="mt-6 grid gap-5" aria-live="polite">
        {online && data && (
          <div className="flex flex-wrap items-end justify-between gap-3">
            <div className="w-full sm:max-w-xs">
              <Select
                label="Mes de la jornada"
                options={monthsBetween(data.earliestMonth, data.currentMonth)}
                value={month || data.month}
                onChange={setMonth}
              />
            </div>
            <p className="text-sm text-ink-muted">
              Última actualización: {formatDateTime(Date.parse(data.updatedAt))}
            </p>
          </div>
        )}
        {!online ? (
          <p role="status">Sin conexión. Conéctate para consultar las métricas.</p>
        ) : query.isError ? (
          <ProblemNotice error={query.error} onRetry={() => void query.refetch()} />
        ) : !data || query.isPlaceholderData ? (
          <div role="status" className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <span className="sr-only">Cargando métricas…</span>
            <Skeleton className="h-24 w-full" />
            <Skeleton className="h-24 w-full" />
            <Skeleton className="h-24 w-full" />
            <Skeleton className="h-24 w-full" />
          </div>
        ) : (
          <Dashboard data={data} />
        )}
      </div>
    </>
  );
}

function Dashboard({ data }: { data: Metrics }) {
  const daily = dailyPoints(data);
  const monthly = monthlyPoints(data);
  const ranking: readonly RankedItem[] = data.ranking.map((item) => ({
    id: item.experienceId,
    label: item.title,
    value: item.reservations,
  }));
  const dailyRows: TableRow[] = data.daily.map((point) => ({
    key: point.date,
    label: formatLongDate(point.date),
    values: [point.reservations, point.people],
  }));
  const monthlyRows: TableRow[] = data.monthly.map((point) => ({
    key: point.month,
    label: monthFormat.format(new Date(`${point.month}-01T00:00:00Z`)),
    values: [point.reservations, point.people],
  }));

  return (
    <>
      {data.reservations === 0 && (
        <p className="rounded-xl bg-surface px-4 py-3">
          No hay reservas confirmadas para este mes. Cuando alguien reserve una de tus experiencias,
          verás aquí cómo se mueve.
        </p>
      )}
      <dl className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <Tile icon="ticket" label="Reservas confirmadas" value={data.reservations} />
        <Tile icon="users" label="Personas reservadas" value={data.people} />
        <Tile icon="calendar" label="Próximas jornadas" value={data.upcoming} />
        <Tile icon="clock" label="Cupos en próximas jornadas" value={data.upcomingPeople} />
      </dl>
      <div className="grid items-start gap-4 lg:grid-cols-2">
        <ChartCard
          id="host-daily"
          className="lg:col-span-2"
          title="Reservas por día"
          description={`Reservas confirmadas según el día de la jornada, en ${monthFormat.format(new Date(`${data.month}-01T00:00:00Z`))}.`}
          chart={
            <ColumnChart
              points={daily}
              summary={summarize('Reservas por día', daily, (key) => formatLongDate(key))}
            />
          }
          table={
            <DataTable
              caption="Reservas y personas por día del mes"
              firstColumn="Día"
              columns={['Reservas', 'Personas']}
              rows={dailyRows}
              empty="No hay reservas en este mes."
            />
          }
        />
        <ChartCard
          id="host-monthly"
          title="Últimos 12 meses"
          description="Reservas confirmadas por mes. El mes elegido va resaltado."
          chart={
            <ColumnChart
              points={monthly}
              summary={summarize('Reservas por mes', monthly, (key) =>
                monthFormat.format(new Date(`${key}-01T00:00:00Z`)),
              )}
            />
          }
          table={
            <DataTable
              caption="Reservas y personas por mes, últimos doce meses"
              firstColumn="Mes"
              columns={['Reservas', 'Personas']}
              rows={monthlyRows}
              empty="No hay datos."
            />
          }
        />
        <ChartCard
          id="host-occupancy"
          title="Ocupación de próximas jornadas"
          description="Cupos con reserva confirmada frente a los cupos que ofreces."
          chart={<OccupancyMeter slots={data.occupancy.slots} booked={data.occupancy.booked} />}
        />
        {data.ranking.length > 0 && (
          <ChartCard
            id="host-ranking"
            title="Experiencias más reservadas"
            description="Reservas confirmadas del mes y su parte del total."
            chart={
              <RankedBars
                items={ranking}
                total={data.reservations}
                unit={['reserva', 'reservas']}
              />
            }
            table={
              <RankedTable
                items={ranking}
                total={data.reservations}
                unit={['reserva', 'reservas']}
                caption="Experiencias más reservadas del mes"
                valueLabel="Reservas"
              />
            }
          />
        )}
        <ChartCard
          id="host-attendance"
          title="Asistencia"
          description="Cuántas de las personas reservadas llegaron a la jornada."
          chart={
            <p className="rounded-lg bg-surface px-3 py-3 text-ink-muted">
              Dato aún no disponible. Lo mostraremos cuando el registro de llegada pueda medir la
              asistencia de cada persona.
            </p>
          }
        />
      </div>
    </>
  );
}
