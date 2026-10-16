import { useState } from 'react';

import { Button, Icon } from '@/shared/ui';

import { ActiveFilters } from '../components/ActiveFilters';
import { CatalogResults } from '../components/CatalogResults';
import { FilterSheet } from '../components/FilterSheet';
import { SearchBar } from '../components/SearchBar';
import { setFilter } from '../filters';
import { useCatalogFilters } from '../useCatalogFilters';

/** Catálogo público del turista (T-1.10a): tarjetas verticales y filtros en la URL. */
export function CatalogPage() {
  const { filters, applyFilters, sheetCount, filtered } = useCatalogFilters();
  const [sheetOpen, setSheetOpen] = useState(false);

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

      <div className="mt-5">
        <CatalogResults
          filters={filters}
          filtered={filtered}
          onClearFilters={() => {
            applyFilters({});
          }}
          gridClassName=""
        />
      </div>
    </>
  );
}
