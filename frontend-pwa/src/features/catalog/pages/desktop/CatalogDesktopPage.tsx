import { useState, type SubmitEvent } from 'react';

import { Button } from '@/shared/ui';

import { ActiveFilters } from '../../components/ActiveFilters';
import { CatalogResults } from '../../components/CatalogResults';
import { FilterFields } from '../../components/FilterFields';
import { SearchBar } from '../../components/SearchBar';
import { omitFilters, setFilter, toSearchParams, type CatalogFilters } from '../../filters';
import { useCatalogFilters } from '../../useCatalogFilters';

/**
 * Filtros siempre visibles en la barra lateral (ADR-0020 §7): el borrador se aplica con "Aplicar
 * filtros" y se reinicia cuando cambia la URL (la clave del componente la deriva de ella).
 */
function FilterPanel({
  filters,
  onApply,
}: {
  filters: CatalogFilters;
  onApply: (filters: CatalogFilters) => void;
}) {
  const [draft, setDraft] = useState(filters);

  function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    onApply(draft);
  }

  return (
    <form onSubmit={handleSubmit} className="grid gap-5">
      <FilterFields draft={draft} onChange={setDraft} intensityColumns={2} />
      <div className="grid gap-2">
        <Button type="submit">Aplicar filtros</Button>
        <Button
          variant="secondary"
          onClick={() => {
            onApply(omitFilters(filters, 'departmentCode', 'category', 'intensity'));
          }}
        >
          Limpiar
        </Button>
      </div>
    </form>
  );
}

/** Catálogo en escritorio: filtros laterales y rejilla de tarjetas de 2–3 columnas. */
export function CatalogDesktopPage() {
  const { filters, applyFilters, filtered } = useCatalogFilters();

  return (
    <>
      <title>Experiencias · Intégrate</title>
      <div className="max-w-3xl">
        <h1 className="text-4xl leading-tight font-bold text-balance text-brand">
          Experiencias comunitarias
        </h1>
        <p className="mt-2 text-lg text-ink-muted">
          Recorridos guiados por asociaciones rurales de Colombia. Cada franja tiene un aforo
          ecológico para cuidar el territorio.
        </p>
      </div>

      <div className="mt-8 grid grid-cols-[17rem_minmax(0,1fr)] items-start gap-10">
        <aside aria-labelledby="filtros-titulo" className="sticky top-24 rounded-xl bg-surface p-5">
          <h2 id="filtros-titulo" className="text-lg font-bold text-ink">
            Filtrar experiencias
          </h2>
          <div className="mt-4">
            <FilterPanel
              key={toSearchParams(omitFilters(filters, 'q')).toString()}
              filters={filters}
              onApply={applyFilters}
            />
          </div>
        </aside>

        <div>
          <div className="flex">
            <SearchBar
              key={filters.q ?? ''}
              initialQuery={filters.q ?? ''}
              onSearch={(query) => {
                applyFilters(setFilter(filters, 'q', query));
              }}
            />
          </div>
          <div className="mt-3">
            <ActiveFilters filters={filters} onChange={applyFilters} />
          </div>
          <div className="mt-5">
            <CatalogResults
              filters={filters}
              filtered={filtered}
              onClearFilters={() => {
                applyFilters({});
              }}
              gridClassName="grid-cols-2 gap-4 xl:grid-cols-3"
            />
          </div>
        </div>
      </div>
    </>
  );
}
