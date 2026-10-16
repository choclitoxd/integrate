import { useEffect, useMemo, useRef } from 'react';

import { pluralize } from '@/shared/lib';
import { Button, ProblemNotice, Skeleton } from '@/shared/ui';

import type { CatalogFilters } from '../filters';
import { useCategories, useExperiences } from '../queries';
import { ExperienceCard } from './ExperienceCard';

function CardSkeleton() {
  return (
    <div className="rounded-xl bg-surface px-4 py-4">
      <Skeleton className="h-6 w-4/5" />
      <Skeleton className="mt-3 h-4 w-1/2" />
      <Skeleton className="mt-2 h-4 w-2/3" />
      <Skeleton className="mt-4 h-4 w-full" />
      <Skeleton className="mt-4 h-6 w-1/3" />
    </div>
  );
}

interface CatalogResultsProps {
  readonly filters: CatalogFilters;
  /** Hay filtros que quitar en el estado vacío. */
  readonly filtered: boolean;
  readonly onClearFilters: () => void;
  /** Columnas de la lista: una en el teléfono, rejilla en escritorio (ADR-0020 §7). */
  readonly gridClassName: string;
}

/**
 * Resultados paginados del catálogo con sus estados (cargando, error, vacío, fin) y el foco tras
 * "Ver más experiencias". Misma lógica en celular y computador; solo cambia la rejilla.
 */
export function CatalogResults({
  filters,
  filtered,
  onClearFilters,
  gridClassName,
}: CatalogResultsProps) {
  const experiences = useExperiences(filters);
  const categories = useCategories();

  const categoryNames = useMemo(
    () => new Map(categories.data?.map((category) => [category.code, category.name])),
    [categories.data],
  );

  const items = useMemo(
    () => experiences.data?.pages.flatMap((page) => page.items) ?? [],
    [experiences.data],
  );
  const total = experiences.data?.pages[0]?.totalElements ?? 0;

  // Tras "Ver más experiencias", el foco pasa a la primera tarjeta nueva.
  const pendingFocus = useRef<number | null>(null);
  const cardLinks = useRef(new Map<number, HTMLAnchorElement>());
  useEffect(() => {
    const index = pendingFocus.current;
    if (index !== null && items.length > index) {
      cardLinks.current.get(index)?.focus();
      pendingFocus.current = null;
    }
  }, [items.length]);

  return (
    <section aria-labelledby="resultados">
      <h2 id="resultados" className="sr-only">
        Resultados
      </h2>
      <p aria-live="polite" className="text-sm font-semibold text-ink-muted">
        {experiences.isPending
          ? 'Buscando experiencias…'
          : experiences.isSuccess
            ? pluralize(total, 'experiencia', 'experiencias')
            : ''}
      </p>

      {experiences.isPending && (
        <div className={`mt-3 grid gap-3 ${gridClassName}`} aria-hidden="true">
          <CardSkeleton />
          <CardSkeleton />
        </div>
      )}

      {experiences.isError && items.length === 0 && (
        <div className="mt-3">
          <ProblemNotice
            error={experiences.error}
            onRetry={() => {
              void experiences.refetch();
            }}
          />
        </div>
      )}

      {experiences.isSuccess && total === 0 && (
        <div className="mt-3 rounded-xl bg-surface px-4 py-6 text-center">
          <p className="font-semibold text-ink">No encontramos experiencias con estos criterios.</p>
          <p className="mt-1 text-ink-muted">Prueba con otro municipio o quita algún filtro.</p>
          {filtered && (
            <Button variant="secondary" className="mt-4" onClick={onClearFilters}>
              Quitar filtros
            </Button>
          )}
        </div>
      )}

      {items.length > 0 && (
        <ul className={`mt-3 grid gap-3 ${gridClassName}`}>
          {items.map((experience, index) => (
            <li key={experience.id}>
              <ExperienceCard
                experience={experience}
                categoryNames={categoryNames}
                linkRef={(link) => {
                  if (link) cardLinks.current.set(index, link);
                  else cardLinks.current.delete(index);
                }}
              />
            </li>
          ))}
        </ul>
      )}

      {experiences.isFetchNextPageError && (
        <div className="mt-3">
          <ProblemNotice
            error={experiences.error}
            onRetry={() => {
              void experiences.fetchNextPage();
            }}
          />
        </div>
      )}

      {experiences.hasNextPage && !experiences.isFetchNextPageError && (
        <Button
          variant="secondary"
          className="mt-4 w-full"
          disabled={experiences.isFetchingNextPage}
          onClick={() => {
            pendingFocus.current = items.length;
            void experiences.fetchNextPage();
          }}
        >
          {experiences.isFetchingNextPage ? 'Cargando…' : 'Ver más experiencias'}
        </Button>
      )}

      {experiences.isSuccess && total > 0 && !experiences.hasNextPage && (
        <p className="mt-5 text-center text-sm text-ink-muted">
          Estas son todas las experiencias disponibles.
        </p>
      )}
    </section>
  );
}
