import { Link } from 'react-router';

import { pluralize } from '@/shared/lib';
import { buttonClassName, Icon, ProblemNotice, Skeleton } from '@/shared/ui';

import { useAdminExperiences } from '../queries';

/**
 * Consola en un celular o tablet (ADR-0042 §4): la administración se diseñó para escritorio, así que
 * aquí solo se informa y se muestra, en solo lectura, cuánto espera revisión.
 */
export function AdminMobileNotice() {
  const pending = useAdminExperiences('PENDING_REVIEW', 0);

  return (
    <>
      <title>Administración · Intégrate</title>
      <h1 className="text-3xl font-bold text-brand">
        La administración se usa desde un computador
      </h1>
      <p className="mt-3 text-ink-muted">
        Las tablas de moderación y la bitácora de auditoría necesitan una pantalla ancha. Abre
        Intégrate en un computador con esta misma cuenta para trabajar en ellas.
      </p>

      <section
        aria-labelledby="pendientes-titulo"
        className="mt-6 rounded-xl border-2 border-border-subtle bg-surface px-4 py-4"
      >
        <h2 id="pendientes-titulo" className="flex items-center gap-2 text-lg font-bold text-ink">
          <Icon name="clock" />
          Pendientes hoy
        </h2>
        {pending.isPending && <Skeleton className="mt-3 h-6 w-40" />}
        {pending.isError && (
          <div className="mt-3">
            <ProblemNotice
              error={pending.error}
              onRetry={() => {
                void pending.refetch();
              }}
            />
          </div>
        )}
        {pending.data && (
          <p className="mt-2 text-ink">
            {pending.data.totalElements === 0
              ? 'No hay experiencias esperando revisión.'
              : `${pluralize(pending.data.totalElements, 'experiencia espera', 'experiencias esperan')} tu revisión.`}
          </p>
        )}
      </section>

      <Link to="/" className={buttonClassName('secondary', 'mt-6 w-full')}>
        Volver al sitio
      </Link>
    </>
  );
}
