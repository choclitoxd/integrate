import { useInfiniteQuery, useQuery } from '@tanstack/react-query';

import { useHttpClient } from '@/shared/api';
import { addDays, todayInBogota } from '@/shared/lib';

import {
  getAvailability,
  getExperience,
  listCategories,
  listDepartments,
  listMunicipalities,
  searchExperiences,
} from './api/catalogApi';
import type { CatalogFilters } from './filters';

/** Ventana de disponibilidad: hoy y los 59 días siguientes en Bogotá (máximo del contrato). */
export const AVAILABILITY_WINDOW_DAYS = 60;
const AVAILABILITY_REFRESH_MS = 30_000;

export const catalogKeys = {
  experiences: (filters: CatalogFilters) => ['catalog', 'experiences', filters] as const,
  experience: (id: string) => ['catalog', 'experience', id] as const,
  /** Prefijo de toda la disponibilidad de una experiencia (para invalidarla tras reservar). */
  availabilityOf: (id: string) => ['catalog', 'availability', id] as const,
  availability: (id: string, dateFrom: string) =>
    ['catalog', 'availability', id, dateFrom] as const,
  departments: ['catalog', 'departments'] as const,
  municipalities: (departmentCode: string) =>
    ['catalog', 'municipalities', departmentCode] as const,
  categories: ['catalog', 'categories'] as const,
};

export function useExperiences(filters: CatalogFilters) {
  const http = useHttpClient();
  return useInfiniteQuery({
    queryKey: catalogKeys.experiences(filters),
    queryFn: ({ pageParam, signal }) => searchExperiences(http, filters, pageParam, signal),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
  });
}

export function useExperience(id: string) {
  const http = useHttpClient();
  return useQuery({
    queryKey: catalogKeys.experience(id),
    queryFn: ({ signal }) => getExperience(http, id, signal),
  });
}

/**
 * Cupos "en tiempo real" sin canal de empuje (ADR-0012 §2): siempre obsoletos, se refrescan al
 * volver a la pestaña y cada 30 s mientras la vista está visible (TanStack pausa en segundo plano).
 */
export function useAvailability(id: string) {
  const http = useHttpClient();
  const dateFrom = todayInBogota();
  const dateTo = addDays(dateFrom, AVAILABILITY_WINDOW_DAYS - 1);
  return useQuery({
    queryKey: catalogKeys.availability(id, dateFrom),
    queryFn: ({ signal }) => getAvailability(http, id, dateFrom, dateTo, signal),
    staleTime: 0,
    refetchOnWindowFocus: true,
    refetchInterval: AVAILABILITY_REFRESH_MS,
  });
}

export function useDepartments() {
  const http = useHttpClient();
  return useQuery({
    queryKey: catalogKeys.departments,
    queryFn: ({ signal }) => listDepartments(http, signal),
    staleTime: Infinity,
  });
}

export function useMunicipalities(departmentCode: string | undefined) {
  const http = useHttpClient();
  return useQuery({
    queryKey: catalogKeys.municipalities(departmentCode ?? ''),
    queryFn: ({ signal }) => listMunicipalities(http, departmentCode ?? '', signal),
    enabled: Boolean(departmentCode),
    staleTime: Infinity,
  });
}

export function useCategories() {
  const http = useHttpClient();
  return useQuery({
    queryKey: catalogKeys.categories,
    queryFn: ({ signal }) => listCategories(http, signal),
    staleTime: Infinity,
  });
}
