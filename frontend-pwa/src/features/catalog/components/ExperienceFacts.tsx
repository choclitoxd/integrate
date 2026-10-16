import { formatDistance, formatDuration, pluralize } from '@/shared/lib';
import { Icon, type IconName } from '@/shared/ui';

import type { ExperienceSummary } from '../api/types';
import { INTENSITY_LABELS } from '../filters';
import { ROUTE_TYPE_LABELS } from '../labels';

interface Fact {
  readonly icon: IconName;
  readonly term: string;
  readonly value: string;
}

/** Los datos de recorrido son opcionales (ADR-0022): solo se muestran los que el anfitrión informó. */
function routeFactsOf(experience: ExperienceSummary): readonly Fact[] {
  const { distanceKm, roundTripMinutes, routeType, stopsCount } = experience;
  const facts: Fact[] = [];
  if (distanceKm !== null) {
    facts.push({
      icon: 'route',
      term: 'Distancia',
      value: routeType
        ? `${formatDistance(distanceKm)} · ${ROUTE_TYPE_LABELS[routeType]}`
        : formatDistance(distanceKm),
    });
  }
  if (roundTripMinutes !== null) {
    facts.push({
      icon: 'clock',
      term: 'Tiempo de ida y vuelta',
      value: formatDuration(roundTripMinutes),
    });
  }
  // "Sin paradas" solo se afirma si el recorrido fue descrito; en las experiencias antiguas no se sabe.
  if (stopsCount > 0 || distanceKm !== null || routeType !== null) {
    facts.push({
      icon: 'mapPin',
      term: 'Paradas',
      value: stopsCount > 0 ? pluralize(stopsCount, 'parada', 'paradas') : 'Sin paradas',
    });
  }
  return facts;
}

function factsOf(experience: ExperienceSummary): readonly Fact[] {
  return [
    { icon: 'clock', term: 'Duración', value: formatDuration(experience.durationMinutes) },
    {
      icon: 'mountain',
      term: 'Intensidad física',
      value: INTENSITY_LABELS[experience.physicalIntensity],
    },
    ...routeFactsOf(experience),
    {
      icon: 'leaf',
      term: 'Aforo ecológico',
      value: `Máx. ${pluralize(experience.maxCapacityPerSlot, 'persona', 'personas')} por franja`,
    },
  ];
}

/** Texto de la tarjeta, sin rótulo visible: cada valor debe entenderse solo. */
function compactValue(fact: Fact): string {
  switch (fact.term) {
    case 'Intensidad física':
      return `Intensidad ${fact.value.toLowerCase()}`;
    case 'Distancia':
      return fact.value.split(' · ')[0] ?? fact.value;
    case 'Tiempo de ida y vuelta':
      return `Ida y vuelta ${fact.value}`;
    default:
      return fact.value;
  }
}

interface ExperienceFactsProps {
  readonly experience: ExperienceSummary;
  /** `compact` en la tarjeta (sin rótulo visible), `full` en el detalle. */
  readonly variant?: 'compact' | 'full';
}

/** Datos operativos de la experiencia como lista de definiciones (lectores de pantalla). */
export function ExperienceFacts({ experience, variant = 'compact' }: ExperienceFactsProps) {
  const full = variant === 'full';
  return (
    <dl className={full ? 'grid gap-4' : 'flex flex-wrap gap-x-4 gap-y-1.5 text-sm'}>
      {factsOf(experience).map((fact) =>
        full ? (
          // Rótulo sobre el valor: el valor largo ("Máx. 12 personas por franja") cabe en 360 px.
          <div key={fact.term} className="grid grid-cols-[auto_1fr] items-center gap-x-3">
            <Icon name={fact.icon} size={24} className="row-span-2 text-brand" />
            <dt className="text-sm text-ink-muted">{fact.term}</dt>
            <dd className="font-semibold text-ink">{fact.value}</dd>
          </div>
        ) : (
          <div key={fact.term} className="flex items-center gap-2 text-ink-muted">
            <Icon name={fact.icon} size={18} className="text-brand" />
            <dt className="sr-only">{fact.term}</dt>
            <dd>{compactValue(fact)}</dd>
          </div>
        ),
      )}
    </dl>
  );
}
