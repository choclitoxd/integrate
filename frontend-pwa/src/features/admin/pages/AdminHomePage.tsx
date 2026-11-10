import { Link } from 'react-router';

import { pluralize } from '@/shared/lib';
import { Icon, ProblemNotice, Skeleton, type IconName } from '@/shared/ui';

import { ChainVerifier } from '../components/ChainVerifier';
import { PageHeader } from '../components/parts';
import { formatInstant, isDoubtfulMeetingPoint } from '../labels';
import { useAdminExperiences, useAssociations } from '../queries';

interface CardProps {
  readonly to: string;
  readonly icon: IconName;
  readonly label: string;
  readonly value: number | undefined;
  readonly hint: string;
}

function SummaryCard({ to, icon, label, value, hint }: CardProps) {
  return (
    <li>
      <Link
        to={to}
        className="flex h-full min-h-11 flex-col gap-1 rounded-xl border-2 border-border-subtle bg-surface px-5 py-4 hover:border-brand hover:bg-brand-soft"
      >
        <span className="flex items-center gap-2 font-semibold text-ink">
          <span className="rounded-lg bg-brand-soft p-1.5 text-brand">
            <Icon name={icon} size={20} />
          </span>
          {label}
        </span>
        {value === undefined ? (
          <Skeleton className="h-10 w-16" />
        ) : (
          <span className="text-4xl font-bold text-brand tabular-nums">{value}</span>
        )}
        <span className="text-sm text-ink-muted">{hint}</span>
      </Link>
    </li>
  );
}

/** Resumen de la consola: lo que espera una decisión del administrador (ADR-0042 §2). */
export function AdminHomePage() {
  const pending = useAdminExperiences('PENDING_REVIEW', 0);
  const suspended = useAdminExperiences('SUSPENDED', 0);
  const associations = useAssociations(0);

  const pendingItems = pending.data?.items ?? [];
  const doubtful = pendingItems.filter((item) =>
    isDoubtfulMeetingPoint(item.meetingPointCheck?.status),
  ).length;
  const failed = [pending, suspended, associations].find((query) => query.isError);

  return (
    <>
      <title>Resumen · Administración · Intégrate</title>
      <PageHeader
        title="Resumen"
        description="Lo que espera una decisión tuya hoy. Cada tarjeta lleva a su sección."
      />

      {failed && (
        <div className="mt-6">
          <ProblemNotice
            error={failed.error}
            onRetry={() => {
              void pending.refetch();
              void suspended.refetch();
              void associations.refetch();
            }}
          />
        </div>
      )}

      <ul aria-label="Indicadores" className="mt-6 grid grid-cols-2 gap-4 xl:grid-cols-4">
        <SummaryCard
          to="/admin/experiencias?estado=PENDING_REVIEW"
          icon="clock"
          label="Por revisar"
          value={pending.data?.totalElements}
          hint="Experiencias enviadas por anfitriones"
        />
        <SummaryCard
          to="/admin/experiencias?estado=PENDING_REVIEW"
          icon="mapPin"
          label="Puntos de encuentro dudosos"
          value={pending.data ? doubtful : undefined}
          hint="Entre las experiencias por revisar"
        />
        <SummaryCard
          to="/admin/experiencias?estado=SUSPENDED"
          icon="alert"
          label="Suspendidas"
          value={suspended.data?.totalElements}
          hint="Fuera del catálogo hasta reactivarlas"
        />
        <SummaryCard
          to="/admin/asociaciones"
          icon="users"
          label="Asociaciones"
          value={associations.data?.totalElements}
          hint="Organizaciones comunitarias registradas"
        />
      </ul>

      <div className="mt-8 grid items-start gap-6 xl:grid-cols-2">
        <section
          aria-labelledby="cola-titulo"
          className="rounded-xl border-2 border-border-subtle bg-canvas px-5 py-5"
        >
          <h2 id="cola-titulo" className="text-xl font-bold text-ink">
            Siguientes en la cola
          </h2>
          {pending.isPending && <Skeleton className="mt-4 h-24" />}
          {pending.isSuccess && pendingItems.length === 0 && (
            <p className="mt-3 text-ink-muted">No hay experiencias esperando revisión.</p>
          )}
          {pendingItems.length > 0 && (
            <>
              <p className="mt-1 text-ink-muted">
                {pluralize(pending.data?.totalElements ?? 0, 'experiencia', 'experiencias')} por
                revisar.
              </p>
              <ul className="mt-3 grid gap-2">
                {pendingItems.slice(0, 5).map((item) => (
                  <li key={item.id}>
                    <Link
                      to={`/admin/experiencias?estado=PENDING_REVIEW&ver=${item.id}`}
                      className="flex min-h-11 items-center justify-between gap-4 rounded-lg px-3 py-2 font-semibold text-brand hover:bg-brand-soft"
                    >
                      <span className="min-w-0 underline underline-offset-4">{item.title}</span>
                      <span className="shrink-0 text-sm font-normal text-ink-muted">
                        {formatInstant(item.createdAt)}
                      </span>
                    </Link>
                  </li>
                ))}
              </ul>
            </>
          )}
        </section>

        <ChainVerifier />
      </div>
    </>
  );
}
