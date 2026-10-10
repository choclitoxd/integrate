import { QueryClientProvider, type QueryClient } from '@tanstack/react-query';
import { useEffect, useState, type ReactNode } from 'react';

import { HttpClientContext, type HttpClient } from '@/shared/api';
import { SessionContext, type SessionStore } from '@/shared/session';

import { createQueryClient } from './queryClient';

interface AppProvidersProps {
  readonly httpClient: HttpClient;
  readonly session: SessionStore;
  /** Las pruebas inyectan un cliente sin reintentos. */
  readonly queryClient?: QueryClient;
  readonly children: ReactNode;
}

export function AppProviders({ httpClient, session, queryClient, children }: AppProvidersProps) {
  const [client] = useState(() => queryClient ?? createQueryClient());

  // Al cerrar sesión no quedan datos del titular en caché (consultas bajo ['me', …]).
  useEffect(
    () =>
      session.subscribe(() => {
        if (session.getState().status === 'anonymous') client.removeQueries({ queryKey: ['me'] });
      }),
    [session, client],
  );

  return (
    <QueryClientProvider client={client}>
      <SessionContext value={session}>
        <HttpClientContext value={httpClient}>{children}</HttpClientContext>
      </SessionContext>
    </QueryClientProvider>
  );
}
