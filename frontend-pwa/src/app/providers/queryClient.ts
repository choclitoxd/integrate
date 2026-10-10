import { QueryClient } from '@tanstack/react-query';

import { shouldRetryRequest } from '@/shared/api';

const FIVE_MINUTES = 5 * 60 * 1000;

/** Valores por defecto de las consultas (ADR-0012 §2); cada consulta puede ajustarlos. */
export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: FIVE_MINUTES,
        retry: shouldRetryRequest,
        refetchOnWindowFocus: false,
      },
    },
  });
}
