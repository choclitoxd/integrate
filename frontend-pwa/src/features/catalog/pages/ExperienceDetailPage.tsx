import type { ReactNode } from 'react';
import { Link, useParams } from 'react-router';

import { ApiError } from '@/shared/api';
import { formatCop } from '@/shared/lib';
import { buttonClassName, Icon, ProblemNotice, Skeleton } from '@/shared/ui';

import type { ExperienceDetail } from '../api/types';
import { DetailHeader, DetailSections } from '../components/ExperienceDetailParts';
import { ExperienceFacts } from '../components/ExperienceFacts';
import { useExperience } from '../queries';

/** Una experiencia inexistente o no aprobada responde 404 (ADR-0006): vista propia, no error genérico. */
export function UnavailableExperience() {
  return (
    <>
      <title>Experiencia no disponible · Intégrate</title>
      <h1 className="text-2xl font-bold text-brand">Esta experiencia no está disponible</h1>
      <p className="mt-3 text-ink-muted">
        Puede que la comunidad la haya retirado o que el enlace esté incompleto.
      </p>
      <Link to="/" className={buttonClassName('secondary', 'mt-6')}>
        Ver otras experiencias
      </Link>
    </>
  );
}

/** Carga, 404 y error del detalle, comunes a la vista móvil y a la de escritorio (ADR-0020 §6). */
export function ExperienceDetailLoader({
  children,
}: {
  children: (detail: ExperienceDetail) => ReactNode;
}) {
  const { experienceId = '' } = useParams();
  const experience = useExperience(experienceId);

  if (experience.isPending) {
    return (
      <div aria-busy="true">
        <p className="sr-only" role="status">
          Cargando la experiencia…
        </p>
        <Skeleton className="h-9 w-4/5" />
        <Skeleton className="mt-3 h-5 w-1/2" />
        <Skeleton className="mt-6 h-28 w-full" />
        <Skeleton className="mt-8 h-40 w-full" />
      </div>
    );
  }
  if (experience.isError) {
    if (experience.error instanceof ApiError && experience.error.status === 404) {
      return <UnavailableExperience />;
    }
    return (
      <>
        <title>Error · Intégrate</title>
        <h1 className="text-2xl font-bold text-brand">No pudimos cargar la experiencia</h1>
        <div className="mt-4">
          <ProblemNotice
            error={experience.error}
            onRetry={() => {
              void experience.refetch();
            }}
          />
        </div>
      </>
    );
  }
  return children(experience.data);
}

function DetailContent({ detail }: { detail: ExperienceDetail }) {
  const { summary } = detail;
  return (
    <>
      <DetailHeader detail={detail} />

      <div className="mt-5 rounded-xl bg-surface px-4 py-4">
        <ExperienceFacts experience={summary} variant="full" />
      </div>

      {/* La acción va con los datos de la ficha, donde se decide; no al final de una página larga. */}
      <div className="mt-4 grid gap-3">
        <p className="flex items-baseline justify-between gap-3 px-1">
          <span className="text-ink-muted">Precio por persona</span>
          <span className="text-2xl font-bold text-ink tabular-nums">
            {formatCop(summary.unitPriceCop)}
          </span>
        </p>
        <Link
          to={`/experiencias/${summary.id}/franjas`}
          className={buttonClassName('primary', 'w-full')}
        >
          <Icon name="calendar" />
          Ver fechas
        </Link>
      </div>

      <DetailSections detail={detail} />
    </>
  );
}

/** Detalle público de una experiencia aprobada (T-1.10a). */
export function ExperienceDetailPage() {
  return (
    <ExperienceDetailLoader>{(detail) => <DetailContent detail={detail} />}</ExperienceDetailLoader>
  );
}
