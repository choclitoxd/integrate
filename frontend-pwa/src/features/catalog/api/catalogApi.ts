import type { HttpClient } from '@/shared/api';

import type { CatalogFilters } from '../filters';
import type {
  Category,
  ExperienceAvailability,
  ExperienceDetail,
  ExperiencePage,
  Place,
} from './types';

export const CATALOG_PAGE_SIZE = 10;

/** `exactOptionalPropertyTypes`: la señal solo viaja si existe. */
const withSignal = (signal: AbortSignal | undefined) => (signal ? { signal } : {});

export function searchExperiences(
  http: HttpClient,
  filters: CatalogFilters,
  page: number,
  signal?: AbortSignal,
): Promise<ExperiencePage> {
  const params = new URLSearchParams({ page: String(page), size: String(CATALOG_PAGE_SIZE) });
  if (filters.q) params.set('q', filters.q);
  if (filters.departmentCode) params.set('departmentCode', filters.departmentCode);
  if (filters.municipalityCode) params.set('municipalityCode', filters.municipalityCode);
  if (filters.category) params.set('category', filters.category);
  if (filters.intensity) params.set('intensity', filters.intensity);
  return http.request<ExperiencePage>(`/v1/experiences?${params.toString()}`, withSignal(signal));
}

export function getExperience(
  http: HttpClient,
  id: string,
  signal?: AbortSignal,
): Promise<ExperienceDetail> {
  return http.request<ExperienceDetail>(
    `/v1/experiences/${encodeURIComponent(id)}`,
    withSignal(signal),
  );
}

export function getAvailability(
  http: HttpClient,
  id: string,
  dateFrom: string,
  dateTo: string,
  signal?: AbortSignal,
): Promise<ExperienceAvailability> {
  const params = new URLSearchParams({ dateFrom, dateTo });
  return http.request<ExperienceAvailability>(
    `/v1/experiences/${encodeURIComponent(id)}/availability?${params.toString()}`,
    withSignal(signal),
  );
}

export function listDepartments(http: HttpClient, signal?: AbortSignal): Promise<Place[]> {
  return http.request<Place[]>('/v1/catalog/departments', withSignal(signal));
}

export function listMunicipalities(
  http: HttpClient,
  departmentCode: string,
  signal?: AbortSignal,
): Promise<Place[]> {
  return http.request<Place[]>(
    `/v1/catalog/departments/${encodeURIComponent(departmentCode)}/municipalities`,
    withSignal(signal),
  );
}

export function listCategories(http: HttpClient, signal?: AbortSignal): Promise<Category[]> {
  return http.request<Category[]>('/v1/catalog/categories', withSignal(signal));
}
