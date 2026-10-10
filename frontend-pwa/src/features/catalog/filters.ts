import type { PhysicalIntensity } from './api/types';

/** Filtros del catálogo. La URL es su fuente de verdad (ADR-0012 §3). */
export interface CatalogFilters {
  readonly q?: string;
  readonly departmentCode?: string;
  readonly municipalityCode?: string;
  readonly category?: string;
  readonly intensity?: PhysicalIntensity;
}

const DEPARTMENT = /^\d{2}$/;
const MUNICIPALITY = /^\d{5}$/;
const CATEGORY = /^[A-Z_]{3,40}$/;
const INTENSITIES: readonly PhysicalIntensity[] = ['LOW', 'MEDIUM', 'HIGH'];

function isIntensity(value: string): value is PhysicalIntensity {
  return (INTENSITIES as readonly string[]).includes(value);
}

/** Lee los filtros válidos de la URL y descarta los que el contrato rechazaría (evita 400/422). */
export function parseFilters(params: URLSearchParams): CatalogFilters {
  const q = params.get('q')?.trim().slice(0, 100);
  const departmentCode = params.get('departamento') ?? '';
  const municipalityCode = params.get('municipio') ?? '';
  const category = params.get('categoria') ?? '';
  const intensity = params.get('intensidad') ?? '';
  const validDepartment = DEPARTMENT.test(departmentCode);
  return {
    ...(q && { q }),
    ...(validDepartment && { departmentCode }),
    // Un municipio solo vale dentro de su departamento (los 2 primeros dígitos DIVIPOLA).
    ...(validDepartment &&
      MUNICIPALITY.test(municipalityCode) &&
      municipalityCode.startsWith(departmentCode) && { municipalityCode }),
    ...(CATEGORY.test(category) && { category }),
    ...(isIntensity(intensity) && { intensity }),
  };
}

/** Parámetros en español para la URL compartible. */
export function toSearchParams(filters: CatalogFilters): URLSearchParams {
  const params = new URLSearchParams();
  if (filters.q) params.set('q', filters.q);
  if (filters.departmentCode) params.set('departamento', filters.departmentCode);
  if (filters.municipalityCode) params.set('municipio', filters.municipalityCode);
  if (filters.category) params.set('categoria', filters.category);
  if (filters.intensity) params.set('intensidad', filters.intensity);
  return params;
}

type FilterKey = keyof CatalogFilters;

/** Copia sin las claves indicadas. Quitar el departamento quita también su municipio. */
export function omitFilters(filters: CatalogFilters, ...keys: FilterKey[]): CatalogFilters {
  const drop = new Set<string>(keys);
  if (drop.has('departmentCode')) drop.add('municipalityCode');
  return Object.fromEntries(Object.entries(filters).filter(([key]) => !drop.has(key)));
}

/** Fija un filtro; un valor vacío lo quita. Cambiar de departamento descarta el municipio. */
export function setFilter<K extends FilterKey>(
  filters: CatalogFilters,
  key: K,
  value: CatalogFilters[K] | '',
): CatalogFilters {
  const base = omitFilters(filters, key);
  return value ? { ...base, [key]: value } : base;
}

/** Filtros del bottom-sheet activos (la búsqueda por texto se cuenta aparte). */
export function countSheetFilters(filters: CatalogFilters): number {
  return [
    filters.departmentCode,
    filters.municipalityCode,
    filters.category,
    filters.intensity,
  ].filter(Boolean).length;
}

export const INTENSITY_LABELS: Readonly<Record<PhysicalIntensity, string>> = {
  LOW: 'Baja',
  MEDIUM: 'Media',
  HIGH: 'Alta',
};
