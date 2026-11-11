import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type ReactNode } from 'react';

import {
  ExperienceForm,
  experienceServerErrors,
  EXPERIENCE_STATUS,
  formatInstant,
  INTENSITY,
  matchesSearchQuery,
  MEETING_POINT,
  Notice,
  StatusBadge,
  TableSearchBar,
  type AdminExperience,
  type ExperienceUpdateRequest,
} from '@/features/admin';
import { ApiError, useHttpClient } from '@/shared/api';
import { formatCop, formatDuration } from '@/shared/lib';
import { useOnlineStatus } from '@/shared/offline';
import { BottomSheet, Button, Icon, ProblemNotice, Skeleton } from '@/shared/ui';

import { HostSchedules } from './HostSchedules';

const base = '/v1/host/experiences';
/** Se trae el máximo permitido para que el buscador y el filtro cubran todas sus experiencias. */
const PAGE_SIZE = 50;
const STATUS_ORDER = [
  'DRAFT',
  'PENDING_REVIEW',
  'APPROVED',
  'REJECTED',
  'SUSPENDED',
  'RETIRED',
] as const;
type StatusFilter = (typeof STATUS_ORDER)[number] | 'ALL';

function Fact({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <dt className="text-sm font-semibold text-ink-muted">{label}</dt>
      <dd className="text-ink">{children}</dd>
    </div>
  );
}

/**
 * «Mis experiencias» del anfitrión: un mini administrador solo de sus experiencias. Reutiliza del
 * panel de administración la ventana emergente grande (ADR-0059), el formulario, las etiquetas de
 * estado y los avisos; la lista es de tarjetas para que se lea bien en el teléfono.
 */
export function HostExperiencesPage() {
  const http = useHttpClient();
  const online = useOnlineStatus();
  const client = useQueryClient();
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [result, setResult] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL');
  const list = useQuery({
    queryKey: ['me', 'host', 'experiences', page],
    queryFn: ({ signal }) =>
      http.request<{ items: AdminExperience[]; totalPages: number }>(
        `${base}?page=${String(page)}&size=${String(PAGE_SIZE)}`,
        { signal },
      ),
    enabled: online,
  });
  const detail = useQuery({
    queryKey: ['me', 'host', 'experience', selected],
    queryFn: ({ signal }) =>
      http.request<AdminExperience>(`${base}/${encodeURIComponent(selected ?? '')}`, { signal }),
    enabled: online && selected !== null,
  });
  const change = useMutation({
    mutationFn: ({
      path,
      method,
      body,
    }: {
      path: string;
      method: 'POST' | 'PUT';
      body?: ExperienceUpdateRequest;
    }) => http.request<AdminExperience>(path, { method, ...(body ? { body } : {}) }),
    onSuccess: (experience) => {
      client.setQueryData(['me', 'host', 'experience', experience.id], experience);
      void client.invalidateQueries({ queryKey: ['me', 'host', 'experiences'] });
    },
  });

  const experience = detail.data;
  const items = list.data?.items ?? [];
  const visible = items.filter(
    (item) =>
      (statusFilter === 'ALL' || item.status === statusFilter) &&
      matchesSearchQuery(query, [
        item.title,
        item.vereda,
        item.meetingPoint,
        item.municipalityCode,
      ]),
  );
  const countOf = (status: StatusFilter) =>
    status === 'ALL' ? items.length : items.filter((item) => item.status === status).length;
  const editable = experience?.status === 'DRAFT' || experience?.status === 'REJECTED';
  const path = `${base}/${encodeURIComponent(selected ?? '')}`;
  const closeFicha = () => {
    change.reset();
    setSelected(null);
  };

  return (
    <>
      <title>Mis experiencias · Intégrate</title>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-3xl font-bold text-brand">Mis experiencias</h1>
        {online && (
          <Button
            onClick={() => {
              change.reset();
              setResult(null);
              setCreating(true);
            }}
          >
            <Icon name="plus" />
            Nueva experiencia
          </Button>
        )}
      </div>

      {result && (
        <div className="mt-4">
          <Notice tone="success">{result}</Notice>
        </div>
      )}

      {!online ? (
        <p role="status" className="mt-4">
          Sin conexión. Conéctate para consultar o actualizar tus experiencias.
        </p>
      ) : (
        <div className="mt-4 grid gap-4">
          {items.length > 0 && (
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div role="group" aria-label="Filtrar por estado" className="flex flex-wrap gap-2">
                {(['ALL', ...STATUS_ORDER] as const)
                  .filter(
                    (status) => status === 'ALL' || status === statusFilter || countOf(status) > 0,
                  )
                  .map((status) => {
                    const current = status === statusFilter;
                    return (
                      <button
                        key={status}
                        type="button"
                        aria-pressed={current}
                        onClick={() => {
                          setStatusFilter(status);
                        }}
                        className="inline-flex min-h-11 items-center gap-1.5 rounded-full border-2 border-border bg-canvas px-4 font-semibold text-ink hover:bg-surface-subtle aria-pressed:border-brand aria-pressed:bg-brand aria-pressed:text-on-brand"
                      >
                        {status === 'ALL' ? 'Todas' : EXPERIENCE_STATUS[status].text}
                        <span className="tabular-nums">({countOf(status)})</span>
                      </button>
                    );
                  })}
              </div>
              <div className="w-full sm:max-w-xs">
                <TableSearchBar
                  label="Buscar en mis experiencias"
                  placeholder="Buscar por nombre o lugar"
                  value={query}
                  onChange={setQuery}
                />
              </div>
            </div>
          )}
          {list.isError ? (
            <ProblemNotice
              error={list.error}
              onRetry={() => {
                void list.refetch();
              }}
            />
          ) : list.isPending ? (
            <div role="status" className="grid gap-3">
              <span className="sr-only">Cargando experiencias…</span>
              <Skeleton className="h-24 w-full" />
              <Skeleton className="h-24 w-full" />
            </div>
          ) : items.length === 0 ? (
            <p>Aún no tienes experiencias. Crea la primera con «Nueva experiencia».</p>
          ) : visible.length === 0 ? (
            <p role="status">Ninguna experiencia coincide con tu búsqueda o filtro.</p>
          ) : (
            <ul className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
              {visible.map((item) => {
                const label = EXPERIENCE_STATUS[item.status];
                const check = item.meetingPointCheck;
                return (
                  <li
                    key={item.id}
                    className="grid min-w-0 content-start gap-3 rounded-xl border-2 border-border-subtle p-4"
                  >
                    <div className="flex flex-wrap items-start justify-between gap-2">
                      <h2 className="min-w-0 text-lg font-bold break-words text-ink">
                        {item.title}
                      </h2>
                      <StatusBadge tone={label.tone}>{label.text}</StatusBadge>
                    </div>
                    <p className="text-ink-muted tabular-nums">
                      {formatCop(item.unitPriceCop)} por persona · {item.maxCapacityPerSlot} cupos
                    </p>
                    {check && (
                      <div className="flex flex-wrap items-center gap-2 text-sm text-ink-muted">
                        <span>Punto de encuentro:</span>
                        <StatusBadge tone={MEETING_POINT[check.status].tone}>
                          {MEETING_POINT[check.status].text}
                        </StatusBadge>
                      </div>
                    )}
                    <Button
                      variant="secondary"
                      aria-label={`Gestionar ${item.title}`}
                      onClick={() => {
                        change.reset();
                        setResult(null);
                        setSelected(item.id);
                      }}
                    >
                      <Icon name="edit" />
                      Gestionar
                    </Button>
                  </li>
                );
              })}
            </ul>
          )}
          <div className="flex flex-wrap gap-2">
            {page > 0 && (
              <Button
                variant="secondary"
                onClick={() => {
                  setPage(page - 1);
                }}
              >
                Anterior
              </Button>
            )}
            {list.data && page + 1 < list.data.totalPages && (
              <Button
                variant="secondary"
                onClick={() => {
                  setPage(page + 1);
                }}
              >
                Siguiente
              </Button>
            )}
          </div>
        </div>
      )}

      {selected !== null && (
        <BottomSheet open size="xl" title={experience?.title ?? 'Experiencia'} onClose={closeFicha}>
          <div className="grid min-w-0 gap-5">
            {detail.isError && (
              <ProblemNotice
                error={detail.error}
                onRetry={() => {
                  void detail.refetch();
                }}
              />
            )}
            {detail.isPending && <p role="status">Cargando experiencia…</p>}
            {experience && (
              <>
                <div className="flex flex-wrap items-center gap-2">
                  <StatusBadge tone={EXPERIENCE_STATUS[experience.status].tone}>
                    {EXPERIENCE_STATUS[experience.status].text}
                  </StatusBadge>
                  <span className="text-sm text-ink-muted">
                    Creada: {formatInstant(experience.createdAt)}
                  </span>
                </div>
                {experience.rejectionReason && (
                  <p className="rounded-lg bg-surface px-3 py-2 break-words text-ink">
                    <span className="font-semibold">Motivo del rechazo: </span>
                    {experience.rejectionReason}
                  </p>
                )}
                <dl className="grid grid-cols-2 gap-x-4 gap-y-3 rounded-lg bg-surface p-3">
                  <Fact label="Precio actual">{formatCop(experience.unitPriceCop)}</Fact>
                  <Fact label="Cupo por fecha">{experience.maxCapacityPerSlot} personas</Fact>
                  <Fact label="Duración">{formatDuration(experience.durationMinutes)}</Fact>
                  <Fact label="Intensidad física">{INTENSITY[experience.physicalIntensity]}</Fact>
                  {experience.meetingPointCheck && (
                    <Fact label="Punto de encuentro">
                      {MEETING_POINT[experience.meetingPointCheck.status].text}
                    </Fact>
                  )}
                </dl>
                <div className="grid gap-3 sm:grid-cols-2">
                  {experience.latitude !== null && experience.longitude !== null && (
                    <Button
                      variant="secondary"
                      disabled={change.isPending}
                      onClick={() => {
                        change.mutate({ path: `${path}/meeting-point/check`, method: 'POST' });
                      }}
                    >
                      <Icon name="mapPin" />
                      Verificar punto de encuentro
                    </Button>
                  )}
                  {experience.status === 'DRAFT' && (
                    <Button
                      disabled={change.isPending}
                      onClick={() => {
                        change.mutate(
                          { path: `${path}/submit`, method: 'POST' },
                          {
                            onSuccess: () => {
                              setResult(`«${experience.title}» se envió a revisión.`);
                              closeFicha();
                            },
                          },
                        );
                      }}
                    >
                      <Icon name="check" />
                      Enviar a revisión
                    </Button>
                  )}
                </div>
                {experience.status === 'REJECTED' && (
                  <p>Guarda las correcciones y luego envía la experiencia de nuevo a revisión.</p>
                )}
                {editable ? (
                  <ExperienceForm
                    key={experience.id}
                    initial={experience}
                    ariaLabel="Editar experiencia"
                    submitLabel="Guardar cambios"
                    busyLabel="Guardando…"
                    pending={change.isPending}
                    error={change.error}
                    onSubmit={(body, fail) => {
                      change.mutate(
                        { path, method: 'PUT', body },
                        {
                          onSuccess: () => {
                            setResult(`Se guardaron los cambios de «${body.title}».`);
                          },
                          onError: (error) => {
                            if (error instanceof ApiError && error.invalidParams.length > 0) {
                              fail(experienceServerErrors(error.invalidParams));
                            }
                          },
                        },
                      );
                    }}
                  />
                ) : (
                  change.isError && <ProblemNotice error={change.error} />
                )}
                <HostSchedules key={experience.id} experience={experience} />
              </>
            )}
          </div>
        </BottomSheet>
      )}

      {creating && (
        <BottomSheet
          open
          size="xl"
          title="Nueva experiencia"
          onClose={() => {
            change.reset();
            setCreating(false);
          }}
        >
          <ExperienceForm
            initial={null}
            ariaLabel="Datos de la nueva experiencia"
            submitLabel="Guardar borrador"
            busyLabel="Guardando…"
            pending={change.isPending}
            error={change.error}
            onSubmit={(body, fail) => {
              change.mutate(
                { path: base, method: 'POST', body },
                {
                  onSuccess: (created) => {
                    setCreating(false);
                    setResult(
                      `«${created.title}» se guardó como borrador. Agrega sus fechas y envíala a revisión.`,
                    );
                    setSelected(created.id);
                  },
                  onError: (error) => {
                    if (error instanceof ApiError && error.invalidParams.length > 0) {
                      fail(experienceServerErrors(error.invalidParams));
                    }
                  },
                },
              );
            }}
          />
        </BottomSheet>
      )}
    </>
  );
}
