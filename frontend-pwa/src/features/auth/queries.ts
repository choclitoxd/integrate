import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { useHttpClient } from '@/shared/api';

import { getCurrentPolicy, getMyConsents, grantConsents, type ConsentAcceptance } from './api';

/** Las consultas del titular viven bajo `['me', …]`: se borran al cerrar sesión (ADR-0013 §2). */
export const authKeys = {
  policy: ['auth', 'privacy-policy'] as const,
  consents: ['me', 'consents'] as const,
};

export function useCurrentPolicy(enabled = true) {
  const http = useHttpClient();
  return useQuery({
    queryKey: authKeys.policy,
    queryFn: ({ signal }) => getCurrentPolicy(http, signal),
    enabled,
  });
}

export function useMyConsents() {
  const http = useHttpClient();
  return useQuery({
    queryKey: authKeys.consents,
    queryFn: ({ signal }) => getMyConsents(http, signal),
    staleTime: 0,
  });
}

export function useGrantConsents() {
  const http = useHttpClient();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: ConsentAcceptance) => grantConsents(http, body),
    onSuccess: (status) => {
      queryClient.setQueryData(authKeys.consents, status);
    },
  });
}
