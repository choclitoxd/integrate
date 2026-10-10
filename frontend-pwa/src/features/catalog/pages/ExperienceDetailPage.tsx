import { Link, useParams } from 'react-router';

import { ApiError } from '@/shared/api';
import { formatCop } from '@/shared/lib';
import { buttonClassName, Icon, ProblemNotice, Skeleton, StickyActionBar } from '@/shared/ui';

import type { ExperienceDetail } from '../api/types';
import { ExperienceFacts } from '../components/ExperienceFacts';
import { placeLabel } from '../labels';
import { useCategories, useExperience } from '../queries';

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

function MapLinks({ latitude, longitude }: { latitude: number; longitude: number }) {
  const coords = `${String(latitude)},${String(longitude)}`;
  return (
    <div className="mt-3 flex flex-wrap gap-2">
      {/* geo: abre la app de mapas del teléfono; el enlace web cubre el escritorio. */}
      <a href={`geo:${coords}?q=${coords}`} className={buttonClassName('secondary')}>
        <Icon name="mapPin" />
        Abrir en mapas
      </a>
      <a
        href={`https://www.openstreetmap.org/?mlat=${String(latitude)}&mlon=${String(longitude)}#map=15/${String(latitude)}/${String(longitude)}`}
        target="_blank"
        rel="noreferrer"
        className={buttonClassName('quiet', 'px-2')}
      >
        Ver en OpenStreetMap
        <Icon name="external" size={18} />
        <span className="sr-only">(se abre en otra pestaña)</span>
      </a>
    </div>
  );
}

function DetailContent({ detail }: { detail: ExperienceDetail }) {
  const { summary } = detail;
  const categories = useCategories();
  const names = summary.categoryCodes
    .map((code) => categories.data?.find((category) => category.code === code)?.name)
    .filter((name): name is string => Boolean(name));

  return (
    <>
      <title>{`${summary.title} · Intégrate`}</title>
      <h1 className="text-3xl leading-tight font-bold text-balance text-ink">{summary.title}</h1>
      <p className="mt-2 flex items-start gap-1.5 text-lg font-semibold text-accent">
        <Icon name="mapPin" className="mt-1" />
        {placeLabel(summary)}
      </p>
      <p className="mt-1 text-ink-muted">
        Organiza <span className="font-semibold text-ink">{summary.associationName}</span>
      </p>

      {names.length > 0 && (
        <ul className="mt-3 flex flex-wrap gap-2" aria-label="Categorías">
          {names.map((name) => (
            <li
              key={name}
              className="rounded-full bg-surface px-2.5 py-0.5 text-sm font-medium text-ink-muted"
            >
              {name}
            </li>
          ))}
        </ul>
      )}

      <div className="mt-5 rounded-xl bg-surface px-4 py-4">
        <ExperienceFacts experience={summary} variant="full" />
      </div>

      <section aria-labelledby="sobre" className="mt-8">
        <h2 id="sobre" className="text-xl font-bold text-ink">
          Sobre la experiencia
        </h2>
        <p className="mt-2 max-w-prose whitespace-pre-line text-ink">{detail.description}</p>
      </section>

      <section aria-labelledby="encuentro" className="mt-8">
        <h2 id="encuentro" className="text-xl font-bold text-ink">
          Punto de encuentro
        </h2>
        <p className="mt-2 whitespace-pre-line text-ink">{detail.meetingPoint}</p>
        {detail.vereda && <p className="mt-1 text-ink-muted">Vereda {detail.vereda}</p>}
        {detail.latitude !== null && detail.longitude !== null && (
          <MapLinks latitude={detail.latitude} longitude={detail.longitude} />
        )}
      </section>

      <section
        aria-labelledby="seguridad"
        className="mt-8 rounded-xl border-2 border-info-border bg-info-bg px-4 py-4"
      >
        <h2 id="seguridad" className="flex items-center gap-2 text-lg font-bold text-info-text">
          <Icon name="shield" size={22} />
          Recomendaciones de seguridad
        </h2>
        <p className="mt-2 whitespace-pre-line text-ink">{detail.safetyGuidelines}</p>
      </section>

      <StickyActionBar
        summary={
          <p>
            <span className="block text-xl font-bold text-ink tabular-nums">
              {formatCop(summary.unitPriceCop)}
            </span>
            <span className="text-sm text-ink-muted">por persona</span>
          </p>
        }
      >
        <Link
          to={`/experiencias/${summary.id}/franjas`}
          className={buttonClassName('primary', 'px-6')}
        >
          <Icon name="calendar" />
          Ver fechas
        </Link>
      </StickyActionBar>
    </>
  );
}

/** Detalle público de una experiencia aprobada (T-1.10a). */
export function ExperienceDetailPage() {
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
  return <DetailContent detail={experience.data} />;
}
