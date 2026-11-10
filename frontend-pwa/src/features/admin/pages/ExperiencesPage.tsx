import { useState } from 'react';
import { Link, useSearchParams } from 'react-router';

import { ApiError } from '@/shared/api';
import { formatCop, formatDuration } from '@/shared/lib';
import { BottomSheet, Button, Icon, ProblemNotice, Skeleton, type IconName } from '@/shared/ui';

import type { AdminExperience, ExperienceStatus, ModerationAction } from '../api';
import { ExperienceForm } from '../components/ExperienceForm';
import { ModerationDialog } from '../components/ModerationDialog';
import { NewExperienceSheet } from '../components/NewExperienceSheet';
import {
  EmptyRow,
  Notice,
  PageHeader,
  Pager,
  StatusBadge,
  TableFrame,
  TableSearchBar,
} from '../components/parts';
import {
  CAPTION_CLASS,
  matchesSearchQuery,
  TABLE_CLASS,
  TD_CLASS,
  TH_CLASS,
} from '../components/tableStyles';
import { experienceServerErrors } from '../forms';
import {
  EXPERIENCE_STATUS,
  formatInstant,
  INTENSITY,
  isDoubtfulMeetingPoint,
  MEETING_POINT,
  STATUS_FILTERS,
} from '../labels';
import { useAdminExperiences, useUpdateAdminExperience } from '../queries';

const STATUSES = new Set<string>(Object.keys(EXPERIENCE_STATUS));
const ALL = 'TODAS';

/** `?estado=`: sin parámetro se abre la cola por revisar; "TODAS" quita el filtro. */
function statusFrom(raw: string | null): ExperienceStatus | null {
  if (raw === ALL) return null;
  return raw !== null && STATUSES.has(raw) ? (raw as ExperienceStatus) : 'PENDING_REVIEW';
}

/** Acciones que el estado admite (mismas transiciones que `ExperienceStatus` en el backend). */
function actionsFor(experience: AdminExperience): readonly ModerationAction[] {
  switch (experience.status) {
    case 'PENDING_REVIEW':
      return ['approve', 'reject'];
    case 'APPROVED':
      return ['suspend', 'retire'];
    case 'SUSPENDED':
      return ['reinstate', 'retire'];
    case 'DRAFT':
    case 'REJECTED':
      return ['retire'];
    case 'RETIRED':
      return [];
  }
}

const ACTION_LABEL: Readonly<Record<ModerationAction, string>> = {
  approve: 'Aprobar y publicar',
  reject: 'Rechazar',
  suspend: 'Suspender',
  reinstate: 'Reactivar',
  retire: 'Retirar',
  'confirm-meeting-point': 'Confirmar el punto de encuentro',
};

const ACTION_ICON: Readonly<Record<ModerationAction, IconName>> = {
  approve: 'check',
  reject: 'close',
  suspend: 'pause',
  reinstate: 'refresh',
  retire: 'close',
  'confirm-meeting-point': 'mapPin',
};

function MeetingPointBadge({ experience }: { experience: AdminExperience }) {
  const check = experience.meetingPointCheck;
  if (!check) return <span className="text-ink-muted">Sin datos</span>;
  const label = MEETING_POINT[check.status];
  return <StatusBadge tone={label.tone}>{label.text}</StatusBadge>;
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <dt className="text-sm font-semibold text-ink-muted">{label}</dt>
      <dd className="text-ink">{children}</dd>
    </div>
  );
}

interface FichaModalProps {
  readonly experience: AdminExperience;
  readonly open: boolean;
  readonly onClose: () => void;
  readonly onAction: (action: ModerationAction) => void;
  readonly onSaved: (message: string) => void;
}

/** Ventana emergente de gestión y edición de la experiencia seleccionada (ADR-0047 §1 y §3). */
function FichaModal({ experience, open, onClose, onAction, onSaved }: FichaModalProps) {
  const status = EXPERIENCE_STATUS[experience.status];
  const check = experience.meetingPointCheck;
  const doubtful = isDoubtfulMeetingPoint(check?.status);
  const actions = actionsFor(experience);
  const editable = experience.status !== 'RETIRED';
  const update = useUpdateAdminExperience();

  return (
    <BottomSheet open={open} onClose={onClose} title={experience.title}>
      <div className="grid gap-5">
        <div className="flex flex-wrap items-center gap-2">
          <StatusBadge tone={status.tone}>{status.text}</StatusBadge>
          <span className="text-sm text-ink-muted">
            Enviada: {formatInstant(experience.createdAt)}
          </span>
        </div>

        {experience.rejectionReason && (
          <p className="rounded-lg bg-surface px-3 py-2 text-ink">
            <span className="font-semibold">Motivo registrado: </span>
            {experience.rejectionReason}
          </p>
        )}

        <dl className="grid grid-cols-2 gap-x-4 gap-y-3 rounded-lg bg-surface p-3">
          <Field label="Precio actual">{formatCop(experience.unitPriceCop)}</Field>
          <Field label="Cupo por fecha">{experience.maxCapacityPerSlot} personas</Field>
          <Field label="Duración">{formatDuration(experience.durationMinutes)}</Field>
          <Field label="Intensidad física">{INTENSITY[experience.physicalIntensity]}</Field>
          <Field label="Categorías">
            {experience.categoryCodes.length > 0 ? experience.categoryCodes.join(', ') : 'Sin dato'}
          </Field>
          {experience.stops.length > 0 && (
            <Field label="Paradas">{experience.stops.map((stop) => stop.name).join(' · ')}</Field>
          )}
        </dl>

        <section aria-label="Verificación del punto de encuentro" className="grid gap-2">
          <h3 className="font-bold text-ink">Punto de encuentro</h3>
          <p className="text-ink">{experience.meetingPoint ?? 'Sin dirección registrada'}</p>
          {experience.latitude !== null && experience.longitude !== null && (
            <p className="font-mono text-sm text-ink-muted tabular-nums">
              {experience.latitude}, {experience.longitude}
            </p>
          )}
          <div className="flex flex-wrap items-center gap-2">
            <MeetingPointBadge experience={experience} />
            {check?.distanceMeters != null && (
              <span className="text-sm text-ink-muted">
                a {check.distanceMeters} m de la dirección escrita
              </span>
            )}
          </div>
          {doubtful && (
            <div className="mt-1 grid gap-3">
              <p className="text-ink-muted">
                Las coordenadas no coinciden con la dirección. Confírmalas solo si conoces el lugar;
                si no, corrígelas abajo o rechaza la experiencia.
              </p>
              <Button
                variant="secondary"
                onClick={() => {
                  onAction('confirm-meeting-point');
                }}
              >
                <Icon name="mapPin" />
                {ACTION_LABEL['confirm-meeting-point']}
              </Button>
            </div>
          )}
        </section>

        {actions.length > 0 && (
          <section
            aria-label="Decisiones de moderación"
            className="grid gap-3 border-t border-border-subtle pt-4"
          >
            <h3 className="font-bold text-ink">Estado de publicación</h3>
            <div className="flex flex-wrap gap-3">
              {actions.map((action, index) => (
                <Button
                  key={action}
                  variant={index === 0 ? 'primary' : 'secondary'}
                  onClick={() => {
                    onAction(action);
                  }}
                >
                  <Icon name={ACTION_ICON[action]} />
                  {ACTION_LABEL[action]}
                </Button>
              ))}
            </div>
          </section>
        )}

        {editable && (
          <div className="grid gap-4 border-t border-border-subtle pt-4">
            <ExperienceForm
              initial={experience}
              heading="Editar datos de la experiencia"
              ariaLabel="Editar datos de la experiencia"
              submitLabel="Guardar cambios"
              busyLabel="Guardando…"
              pending={update.isPending}
              error={update.error}
              onSubmit={(body, fail) => {
                update.mutate(
                  { id: experience.id, body },
                  {
                    onSuccess: (updated) => {
                      onSaved(`Se guardaron los cambios de «${updated.title}».`);
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
          </div>
        )}
      </div>
    </BottomSheet>
  );
}

/** Cola de moderación y edición administrativa de experiencias en ventana emergente (ADR-0042, ADR-0047). */
export function ExperiencesPage() {
  const [params, setParams] = useSearchParams();
  const status = statusFrom(params.get('estado'));
  const page = Math.max(Number.parseInt(params.get('pagina') ?? '1', 10) || 1, 1) - 1;
  const selectedId = params.get('ver');
  const experiences = useAdminExperiences(status, page);
  const [dialog, setDialog] = useState<ModerationAction | null>(null);
  const [newOpen, setNewOpen] = useState(false);
  const [result, setResult] = useState<string | null>(null);
  const [query, setQuery] = useState('');

  const allItems = experiences.data?.items ?? [];
  const items = allItems.filter((item) =>
    matchesSearchQuery(query, [
      item.title,
      item.municipalityCode,
      item.vereda,
      item.meetingPoint,
      EXPERIENCE_STATUS[item.status].text,
      ...item.categoryCodes,
    ]),
  );
  const selected = allItems.find((item) => item.id === selectedId);
  const statusParam = status ?? ALL;

  const hrefFor = (next: { estado?: string; pagina?: number; ver?: string | null }) => {
    const queryParams = new URLSearchParams({ estado: next.estado ?? statusParam });
    const nextPage = next.pagina ?? (next.estado === undefined ? page + 1 : 1);
    if (nextPage > 1) queryParams.set('pagina', String(nextPage));
    if (next.ver) queryParams.set('ver', next.ver);
    return `?${queryParams.toString()}`;
  };

  return (
    <>
      <title>Experiencias · Administración · Intégrate</title>
      <PageHeader
        title="Experiencias"
        description="Revisa lo que envían los anfitriones, edita sus datos y decide qué se publica en el catálogo."
        actions={
          <Button
            onClick={() => {
              setResult(null);
              setNewOpen(true);
            }}
          >
            <Icon name="plus" />
            <span className="hidden sm:inline">Nueva experiencia</span>
          </Button>
        }
      />

      <div className="mt-6 flex flex-wrap items-center justify-between gap-3">
        <nav aria-label="Filtrar por estado">
          <ul className="flex flex-wrap gap-2">
            {STATUS_FILTERS.map((filter) => {
              const value = filter.value ?? ALL;
              const current = value === statusParam;
              return (
                <li key={value}>
                  <Link
                    to={hrefFor({ estado: value })}
                    aria-current={current ? 'true' : undefined}
                    onClick={() => {
                      setResult(null);
                    }}
                    className="inline-flex min-h-11 items-center rounded-full border-2 border-border bg-canvas px-4 font-semibold text-ink hover:bg-surface-subtle aria-[current=true]:border-brand aria-[current=true]:bg-brand aria-[current=true]:text-on-brand"
                  >
                    {filter.text}
                  </Link>
                </li>
              );
            })}
          </ul>
        </nav>
        <div className="w-full sm:max-w-xs">
          <TableSearchBar label="Buscar en experiencias" value={query} onChange={setQuery} />
        </div>
      </div>

      {result && (
        <div className="mt-4">
          <Notice tone="success">{result}</Notice>
        </div>
      )}

      <div className="mt-4 min-w-0">
        {experiences.isError ? (
          <ProblemNotice
            error={experiences.error}
            onRetry={() => {
              void experiences.refetch();
            }}
          />
        ) : (
          <TableFrame
            title={STATUS_FILTERS.find((filter) => filter.value === status)?.text}
            count={experiences.data?.totalElements}
          >
            <table className={TABLE_CLASS}>
              <caption className={CAPTION_CLASS}>
                {STATUS_FILTERS.find((filter) => filter.value === status)?.text}
                {experiences.data && (
                  <span className="font-normal text-ink-muted">
                    {' '}
                    · {experiences.data.totalElements}
                  </span>
                )}
              </caption>
              <thead>
                <tr>
                  <th scope="col" className={TH_CLASS}>
                    Experiencia
                  </th>
                  <th scope="col" className={TH_CLASS}>
                    Municipio
                  </th>
                  <th scope="col" className={TH_CLASS}>
                    Precio
                  </th>
                  <th scope="col" className={TH_CLASS}>
                    Punto de encuentro
                  </th>
                  <th scope="col" className={TH_CLASS}>
                    Estado
                  </th>
                  <th scope="col" className={TH_CLASS}>
                    Enviada
                  </th>
                  <th scope="col" className={TH_CLASS}>
                    Acciones
                  </th>
                </tr>
              </thead>
              <tbody>
                {experiences.isPending && (
                  <EmptyRow columns={7}>
                    <span role="status">Cargando experiencias…</span>
                    <Skeleton className="mx-auto mt-3 h-6 w-2/3" />
                  </EmptyRow>
                )}
                {experiences.isSuccess && items.length === 0 && (
                  <EmptyRow columns={7}>No hay experiencias en este estado.</EmptyRow>
                )}
                {items.map((item) => {
                  const label = EXPERIENCE_STATUS[item.status];
                  const current = item.id === selectedId;
                  return (
                    <tr key={item.id} className={current ? 'bg-brand-soft' : undefined}>
                      <th scope="row" className={`${TD_CLASS} font-semibold text-ink`}>
                        {item.title}
                      </th>
                      <td className={`${TD_CLASS} tabular-nums`}>{item.municipalityCode}</td>
                      <td className={`${TD_CLASS} whitespace-nowrap tabular-nums`}>
                        {formatCop(item.unitPriceCop)}
                      </td>
                      <td className={TD_CLASS}>
                        <MeetingPointBadge experience={item} />
                      </td>
                      <td className={TD_CLASS}>
                        <StatusBadge tone={label.tone}>{label.text}</StatusBadge>
                      </td>
                      <td className={`${TD_CLASS} text-sm text-ink-muted`}>
                        {formatInstant(item.createdAt)}
                      </td>
                      <td className={TD_CLASS}>
                        <Button
                          variant="secondary"
                          aria-label={`Gestionar ${item.title}`}
                          onClick={() => {
                            setResult(null);
                            setParams(hrefFor({ ver: item.id }).slice(1));
                          }}
                        >
                          <Icon name="edit" />
                          <span className="hidden sm:inline">Gestionar</span>
                        </Button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </TableFrame>
        )}
        {experiences.data && (
          <Pager
            label="experiencias"
            page={experiences.data.page}
            totalPages={experiences.data.totalPages}
            totalElements={experiences.data.totalElements}
            onChange={(next) => {
              setParams(hrefFor({ pagina: next + 1 }).slice(1));
            }}
          />
        )}
      </div>

      {selected && (
        <FichaModal
          key={selected.id}
          experience={selected}
          open={!dialog}
          onClose={() => {
            setParams(hrefFor({ ver: null }).slice(1));
          }}
          onAction={(action) => {
            setResult(null);
            setDialog(action);
          }}
          onSaved={(message) => {
            setResult(message);
            setParams(hrefFor({ ver: null }).slice(1));
          }}
        />
      )}

      {selected && dialog && (
        <ModerationDialog
          key={`${selected.id}-${dialog}`}
          experience={selected}
          action={dialog}
          onClose={() => {
            setDialog(null);
          }}
          onDone={(message) => {
            setDialog(null);
            setResult(message);
            setParams(hrefFor({ ver: null }).slice(1));
          }}
        />
      )}

      {newOpen && (
        <NewExperienceSheet
          onClose={() => {
            setNewOpen(false);
          }}
          onCreated={(title) => {
            setNewOpen(false);
            setResult(`Se creó la experiencia «${title}».`);
          }}
        />
      )}
    </>
  );
}
