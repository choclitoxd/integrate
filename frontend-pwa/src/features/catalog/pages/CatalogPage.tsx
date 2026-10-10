import { useEffect, useMemo, useRef, useState } from 'react';
import { useSearchParams } from 'react-router';

import { pluralize } from '@/shared/lib';
import { Button, Icon, ProblemNotice, Skeleton } from '@/shared/ui';

import { ActiveFilters } from '../components/ActiveFilters';
import { ExperienceCard } from '../components/ExperienceCard';
import { FilterSheet } from '../components/FilterSheet';
import { SearchBar } from '../components/SearchBar';
import {
  countSheetFilters,
  parseFilters,
  setFilter,
  toSearchParams,
  type CatalogFilters,
} from '../filters';
import { useCategories, useExperiences } from '../queries';

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

/** Catálogo público del turista (T-1.10a): tarjetas verticales y filtros en la URL. */
export function CatalogPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const filters = useMemo(() => parseFilters(searchParams), [searchParams]);
  const [sheetOpen, setSheetOpen] = useState(false);
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

  function applyFilters(next: CatalogFilters) {
    setSearchParams(toSearchParams(next), { replace: true });
  }

  const sheetCount = countSheetFilters(filters);

  return (
    <>
      <title>Experiencias · Intégrate</title>
      <h1 className="text-3xl leading-tight font-bold text-balance text-brand">
        Experiencias comunitarias
      </h1>
      <p className="mt-2 text-ink-muted">
        Recorridos guiados por asociaciones rurales de Colombia. Cada franja tiene un aforo
        ecológico para cuidar el territorio.
      </p>

      <div className="mt-5 flex gap-2">
        <SearchBar
          key={filters.q ?? ''}
          initialQuery={filters.q ?? ''}
          onSearch={(query) => {
            applyFilters(setFilter(filters, 'q', query));
          }}
        />
        <Button
          variant="secondary"
          className="shrink-0 px-3"
          aria-haspopup="dialog"
          aria-label={sheetCount > 0 ? `Filtros, ${String(sheetCount)} activos` : 'Filtros'}
          onClick={() => {
            setSheetOpen(true);
          }}
        >
          <Icon name="filters" size={22} />
          Filtros
          {sheetCount > 0 && (
            <span className="inline-flex min-w-6 items-center justify-center rounded-full bg-brand px-1.5 text-sm text-on-brand tabular-nums">
              {sheetCount}
            </span>
          )}
        </Button>
      </div>

      <div className="mt-3">
        <ActiveFilters filters={filters} onChange={applyFilters} />
      </div>

      <FilterSheet
        open={sheetOpen}
        onClose={() => {
          setSheetOpen(false);
        }}
        filters={filters}
        onApply={(next) => {
          setSheetOpen(false);
          applyFilters(next);
        }}
      />

      <section aria-labelledby="resultados" className="mt-5">
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
          <div className="mt-3 grid gap-3" aria-hidden="true">
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
            <p className="font-semibold text-ink">
              No encontramos experiencias con estos criterios.
            </p>
            <p className="mt-1 text-ink-muted">Prueba con otro municipio o quita algún filtro.</p>
            {(sheetCount > 0 || filters.q) && (
              <Button
                variant="secondary"
                className="mt-4"
                onClick={() => {
                  applyFilters({});
                }}
              >
                Quitar filtros
              </Button>
            )}
          </div>
        )}

        {items.length > 0 && (
          <ul className="mt-3 grid gap-3">
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
    </>
  );
}
