import { useInfiniteQuery } from '@tanstack/react-query';

import { useHttpClient } from '@/shared/api';

import { listManagedHosts } from './api';

/** Bajo `['me', …]`: se borra al cerrar sesión (ADR-0013 §2). */
export const enrollmentKeys = {
  hosts: ['me', 'gestor', 'hosts'] as const,
};

/** Anfitriones de la jurisdicción, página a página con "Ver más" (ADR-0018 §6). */
export function useManagedHosts() {
  const http = useHttpClient();
  return useInfiniteQuery({
    queryKey: enrollmentKeys.hosts,
    queryFn: ({ pageParam, signal }) => listManagedHosts(http, pageParam, signal),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
    staleTime: 0,
  });
}
