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

const ADMIN_SHORTCUTS: readonly {
  readonly to: string;
  readonly title: string;
  readonly body: string;
  readonly icon: IconName;
}[] = [
  {
    to: '/admin/experiencias',
    title: 'Moderación de experiencias',
    body: 'Revisa las experiencias enviadas por anfitriones, puntos de encuentro y catálogo público.',
    icon: 'mountain',
  },
  {
    to: '/admin/asociaciones',
    title: 'Asociaciones comunitarias',
    body: 'Consulta y registra las organizaciones comunitarias con NIT y RNT vigentes.',
    icon: 'users',
  },
  {
    to: '/admin/personas',
    title: 'Personas, anfitriones y gestores',
    body: 'Busca cuentas por correo o documento, acredita gestores de enlace y asigna anfitriones.',
    icon: 'user',
  },
  {
    to: '/admin/auditoria',
    title: 'Bitácora de auditoría',
    body: 'Filtra eventos de seguridad, verifica la cadena criptográfica y exporta reportes CSV.',
    icon: 'shield',
  },
];

/** Resumen de la consola: plantilla de inicio del administrador con indicadores y accesos directos (ADR-0042 §2, ADR-0044 §1). */
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
        description="Panel de administración comunitaria. Revisa lo pendiente de hoy o entra a cada módulo."
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
        <section aria-labelledby="cola-titulo" className="rounded-xl bg-surface px-5 py-5">
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
                      className="flex min-h-11 items-center justify-between gap-4 rounded-xl border-2 border-border-subtle bg-canvas px-4 py-3 font-semibold text-brand hover:border-brand hover:bg-brand-soft"
                    >
                      <span className="min-w-0">{item.title}</span>
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

      <section aria-labelledby="accesos-admin-titulo" className="mt-8">
        <h2 id="accesos-admin-titulo" className="text-xl font-bold text-ink">
          Módulos de administración
        </h2>
        <ul className="mt-3 grid gap-3 sm:grid-cols-2">
          {ADMIN_SHORTCUTS.map((item) => (
            <li key={item.to}>
              <Link
                to={item.to}
                className="flex h-full min-h-11 items-start gap-3 rounded-xl border-2 border-border-subtle bg-surface px-4 py-4 transition-colors duration-150 hover:border-brand hover:bg-brand-soft"
              >
                <span className="rounded-lg bg-brand-soft p-2 text-brand">
                  <Icon name={item.icon} size={24} />
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block text-lg font-bold text-ink">{item.title}</span>
                  <span className="mt-0.5 block text-sm text-ink-muted">{item.body}</span>
                </span>
                <Icon name="chevronRight" className="mt-1 shrink-0 text-brand" />
              </Link>
            </li>
          ))}
        </ul>
      </section>
    </>
  );
}
