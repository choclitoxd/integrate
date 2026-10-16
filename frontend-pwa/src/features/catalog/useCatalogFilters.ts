import { useMemo } from 'react';
import { useSearchParams } from 'react-router';

import { countSheetFilters, parseFilters, toSearchParams, type CatalogFilters } from './filters';

/** Filtros del catálogo con la URL como fuente de verdad (ADR-0012 §3), para ambas vistas. */
export function useCatalogFilters() {
  const [searchParams, setSearchParams] = useSearchParams();
  const filters = useMemo(() => parseFilters(searchParams), [searchParams]);
  const sheetCount = countSheetFilters(filters);

  function applyFilters(next: CatalogFilters) {
    setSearchParams(toSearchParams(next), { replace: true });
  }

  return {
    filters,
    applyFilters,
    sheetCount,
    filtered: sheetCount > 0 || Boolean(filters.q),
  };
}
