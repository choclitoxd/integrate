import { formatDuration, pluralize } from '@/shared/lib';
import { Icon, type IconName } from '@/shared/ui';

import type { ExperienceSummary } from '../api/types';
import { INTENSITY_LABELS } from '../filters';

interface Fact {
  readonly icon: IconName;
  readonly term: string;
  readonly value: string;
}

function factsOf(experience: ExperienceSummary): readonly Fact[] {
  return [
    { icon: 'clock', term: 'Duración', value: formatDuration(experience.durationMinutes) },
    {
      icon: 'mountain',
      term: 'Intensidad física',
      value: INTENSITY_LABELS[experience.physicalIntensity],
    },
    {
      icon: 'leaf',
      term: 'Aforo ecológico',
      value: `Máx. ${pluralize(experience.maxCapacityPerSlot, 'persona', 'personas')} por franja`,
    },
  ];
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
            <dd>
              {fact.term === 'Intensidad física'
                ? `Intensidad ${fact.value.toLowerCase()}`
                : fact.value}
            </dd>
          </div>
        ),
      )}
    </dl>
  );
}
