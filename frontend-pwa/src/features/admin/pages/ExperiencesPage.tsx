import { useState } from 'react';
import { Link, useSearchParams } from 'react-router';

import { formatCop, formatDuration } from '@/shared/lib';
import { Button, Icon, ProblemNotice, Skeleton } from '@/shared/ui';

import type { AdminExperience, ExperienceStatus, ModerationAction } from '../api';
import { ModerationDialog } from '../components/ModerationDialog';
import { EmptyRow, Notice, PageHeader, Pager, StatusBadge, TableFrame } from '../components/parts';
import { CAPTION_CLASS, TABLE_CLASS, TD_CLASS, TH_CLASS } from '../components/tableStyles';
import {
  EXPERIENCE_STATUS,
  formatInstant,
  INTENSITY,
  isDoubtfulMeetingPoint,
  MEETING_POINT,
  STATUS_FILTERS,
} from '../labels';
import { useAdminExperiences } from '../queries';

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

interface FichaProps {
  readonly experience: AdminExperience;
  readonly closeTo: string;
  readonly onAction: (action: ModerationAction) => void;
}

/** Ficha de la experiencia seleccionada, con las acciones justo después de los datos que confirman. */
function Ficha({ experience, closeTo, onAction }: FichaProps) {
  const status = EXPERIENCE_STATUS[experience.status];
  const check = experience.meetingPointCheck;
  const doubtful = isDoubtfulMeetingPoint(check?.status);
  const actions = actionsFor(experience);

  return (
    <aside
      aria-label="Ficha de la experiencia"
      className="rounded-xl border-2 border-border-subtle bg-surface px-5 py-5"
    >
      <div className="flex items-start justify-between gap-3">
        <h2 className="text-xl font-bold text-ink">{experience.title}</h2>
        <Link
          to={closeTo}
          aria-label="Cerrar la ficha"
          className="inline-flex size-11 shrink-0 items-center justify-center rounded-lg text-ink hover:bg-surface-subtle"
        >
          <Icon name="close" size={22} />
        </Link>
      </div>
      <div className="mt-2">
        <StatusBadge tone={status.tone}>{status.text}</StatusBadge>
      </div>

      {experience.rejectionReason && (
        <p className="mt-4 rounded-lg bg-surface px-3 py-2 text-ink">
          <span className="font-semibold">Motivo registrado: </span>
          {experience.rejectionReason}
        </p>
      )}

      <dl className="mt-4 grid grid-cols-2 gap-x-4 gap-y-3">
        <Field label="Precio por persona">{formatCop(experience.unitPriceCop)}</Field>
        <Field label="Cupo por fecha">{experience.maxCapacityPerSlot} personas</Field>
        <Field label="Duración">{formatDuration(experience.durationMinutes)}</Field>
        <Field label="Intensidad física">{INTENSITY[experience.physicalIntensity]}</Field>
        <Field label="Municipio (código DANE)">{experience.municipalityCode}</Field>
        <Field label="Vereda">{experience.vereda ?? 'Sin dato'}</Field>
        <Field label="Enviada">{formatInstant(experience.createdAt)}</Field>
        <Field label="Categorías">
          {experience.categoryCodes.length > 0 ? experience.categoryCodes.join(', ') : 'Sin dato'}
        </Field>
      </dl>

      <h3 className="mt-5 font-bold text-ink">Descripción</h3>
      <p className="mt-1 whitespace-pre-line text-ink">{experience.description}</p>

      {experience.safetyGuidelines && (
        <>
          <h3 className="mt-4 font-bold text-ink">Recomendaciones de seguridad</h3>
          <p className="mt-1 whitespace-pre-line text-ink">{experience.safetyGuidelines}</p>
        </>
      )}

      {experience.stops.length > 0 && (
        <>
          <h3 className="mt-4 font-bold text-ink">Paradas</h3>
          <ol className="mt-1 list-decimal pl-5 text-ink">
            {experience.stops.map((stop) => (
              <li key={stop.name}>
                {stop.name}
                {stop.description && <span className="text-ink-muted"> — {stop.description}</span>}
              </li>
            ))}
          </ol>
        </>
      )}

      <h3 className="mt-4 font-bold text-ink">Punto de encuentro</h3>
      <p className="mt-1 text-ink">{experience.meetingPoint ?? 'Sin dirección registrada'}</p>
      {experience.latitude !== null && experience.longitude !== null && (
        <p className="font-mono text-sm text-ink-muted tabular-nums">
          {experience.latitude}, {experience.longitude}
        </p>
      )}
      <div className="mt-2 flex flex-wrap items-center gap-2">
        <MeetingPointBadge experience={experience} />
        {check?.distanceMeters != null && (
          <span className="text-sm text-ink-muted">
            a {check.distanceMeters} m de la dirección escrita
          </span>
        )}
      </div>
      {doubtful && (
        <div className="mt-3 grid gap-3">
          <p className="text-ink-muted">
            Las coordenadas no coinciden con la dirección. Confírmalas solo si conoces el lugar; si
            no, rechaza la experiencia para que el anfitrión las corrija.
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

      {actions.length > 0 && (
        <div className="mt-6 grid gap-3 border-t border-border-subtle pt-5">
          {actions.map((action, index) => (
            <Button
              key={action}
              variant={index === 0 ? 'primary' : 'secondary'}
              onClick={() => {
                onAction(action);
              }}
            >
              {ACTION_LABEL[action]}
            </Button>
          ))}
        </div>
      )}
    </aside>
  );
}

/** Cola de moderación de experiencias con ficha lateral (ADR-0042 §2 y §5). */
export function ExperiencesPage() {
  const [params, setParams] = useSearchParams();
  const status = statusFrom(params.get('estado'));
  const page = Math.max(Number.parseInt(params.get('pagina') ?? '1', 10) || 1, 1) - 1;
  const selectedId = params.get('ver');
  const experiences = useAdminExperiences(status, page);
  const [dialog, setDialog] = useState<ModerationAction | null>(null);
  const [result, setResult] = useState<string | null>(null);

  const items = experiences.data?.items ?? [];
  const selected = items.find((item) => item.id === selectedId);
  const statusParam = status ?? ALL;

  const hrefFor = (next: { estado?: string; pagina?: number; ver?: string | null }) => {
    const query = new URLSearchParams({ estado: next.estado ?? statusParam });
    const nextPage = next.pagina ?? (next.estado === undefined ? page + 1 : 1);
    if (nextPage > 1) query.set('pagina', String(nextPage));
    if (next.ver) query.set('ver', next.ver);
    return `?${query.toString()}`;
  };

  return (
    <>
      <title>Experiencias · Administración · Intégrate</title>
      <PageHeader
        title="Experiencias"
        description="Revisa lo que envían los anfitriones y decide qué se publica en el catálogo."
      />

      <nav aria-label="Filtrar por estado" className="mt-6">
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

      {result && (
        <div className="mt-4">
          <Notice tone="success">{result}</Notice>
        </div>
      )}

      <div
        className={`mt-4 grid items-start gap-6 ${selected ? 'grid-cols-[minmax(0,1fr)_24rem]' : ''}`}
      >
        <div className="min-w-0">
          {experiences.isError ? (
            <ProblemNotice
              error={experiences.error}
              onRetry={() => {
                void experiences.refetch();
              }}
            />
          ) : (
            <TableFrame>
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
                  </tr>
                </thead>
                <tbody>
                  {experiences.isPending && (
                    <EmptyRow columns={6}>
                      <span role="status">Cargando experiencias…</span>
                      <Skeleton className="mx-auto mt-3 h-6 w-2/3" />
                    </EmptyRow>
                  )}
                  {experiences.isSuccess && items.length === 0 && (
                    <EmptyRow columns={6}>No hay experiencias en este estado.</EmptyRow>
                  )}
                  {items.map((item) => {
                    const label = EXPERIENCE_STATUS[item.status];
                    const current = item.id === selectedId;
                    return (
                      <tr key={item.id} className={current ? 'bg-brand-soft' : undefined}>
                        <th scope="row" className={`${TD_CLASS} font-semibold`}>
                          <Link
                            to={hrefFor({ ver: item.id })}
                            aria-current={current ? 'true' : undefined}
                            className="inline-flex min-h-11 items-center text-brand underline underline-offset-4"
                          >
                            {item.title}
                          </Link>
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
          <Ficha
            experience={selected}
            closeTo={hrefFor({ ver: null })}
            onAction={(action) => {
              setResult(null);
              setDialog(action);
            }}
          />
        )}
      </div>

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
          }}
        />
      )}
    </>
  );
}
