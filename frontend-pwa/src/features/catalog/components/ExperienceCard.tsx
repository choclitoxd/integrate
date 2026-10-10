import type { Ref } from 'react';
import { Link } from 'react-router';

import { formatCop } from '@/shared/lib';
import { Icon } from '@/shared/ui';

import type { ExperienceSummary } from '../api/types';
import { placeLabel } from '../labels';
import { ExperienceFacts } from './ExperienceFacts';

interface ExperienceCardProps {
  readonly experience: ExperienceSummary;
  readonly categoryNames: ReadonlyMap<string, string>;
  /** Para mover el foco a la primera tarjeta nueva tras "Ver más experiencias". */
  readonly linkRef?: Ref<HTMLAnchorElement>;
}

/**
 * Tarjeta vertical tipográfica (ADR-0012 §7): el contrato no trae imagen y no se inventa.
 * Un único enlace en el título se extiende a toda la tarjeta, sin enlaces anidados.
 */
export function ExperienceCard({ experience, categoryNames, linkRef }: ExperienceCardProps) {
  const categories = experience.categoryCodes
    .map((code) => categoryNames.get(code))
    .filter((name): name is string => Boolean(name));

  return (
    <article className="relative rounded-xl bg-surface px-4 pt-4 pb-3 transition-colors duration-150 hover:bg-brand-soft">
      <h2 className="text-xl leading-snug font-bold text-balance text-ink">
        <Link
          ref={linkRef}
          to={`/experiencias/${experience.id}`}
          className="rounded-sm after:absolute after:inset-0 after:rounded-xl"
        >
          {experience.title}
        </Link>
      </h2>
      <p className="mt-1.5 flex items-start gap-1.5 font-semibold text-accent">
        <Icon name="mapPin" size={18} className="mt-0.5" />
        {placeLabel(experience)}
      </p>
      <p className="mt-0.5 text-sm text-ink-muted">{experience.associationName}</p>

      <div className="mt-3">
        <ExperienceFacts experience={experience} />
      </div>

      {categories.length > 0 && (
        <ul className="mt-3 flex flex-wrap gap-2" aria-label="Categorías">
          {categories.map((name) => (
            <li
              key={name}
              className="rounded-full bg-canvas px-2.5 py-0.5 text-sm font-medium text-ink-muted"
            >
              {name}
            </li>
          ))}
        </ul>
      )}

      <p className="mt-3 flex items-baseline justify-between gap-2 border-t border-border-subtle pt-3">
        <span>
          <span className="text-lg font-bold text-ink tabular-nums">
            {formatCop(experience.unitPriceCop)}
          </span>{' '}
          <span className="text-sm text-ink-muted">por persona</span>
        </span>
        <Icon name="chevronRight" className="self-center text-brand" />
      </p>
    </article>
  );
}
