import { QueryClient } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { vi } from 'vitest';

import { AppProviders } from '@/app/providers/AppProviders';
import { routes } from '@/app/routes';
import { ConsentGate } from '@/features/auth';
import type { HttpClient, RequestOptions } from '@/shared/api';
import { createSessionStore } from '@/shared/session';

/** Respuesta simulada por pathname: un valor, un Error para rechazar, o una función de la URL. */
export type FakeRoutes = Readonly<Record<string, unknown>>;

/**
 * HttpClient en memoria: resuelve por pathname exacto (sin query) contra `fakeRoutes`.
 * Una ruta sin respuesta falla la prueba con un mensaje claro.
 */
export function createFakeHttp(fakeRoutes: FakeRoutes) {
  const request = vi.fn((path: string, options: RequestOptions = {}) => {
    const url = new URL(path, 'http://test');
    const entry = fakeRoutes[url.pathname];
    if (entry === undefined) return Promise.reject(new Error(`Sin respuesta simulada: ${path}`));
    const value: unknown =
      typeof entry === 'function'
        ? (entry as (u: URL, o: RequestOptions) => unknown)(url, options)
        : entry;
    return value instanceof Error ? Promise.reject(value) : Promise.resolve(value);
  });
  return { request: request as HttpClient['request'], calls: request.mock.calls };
}

export interface RenderAppOptions {
  /** Sesión iniciada con estos roles; por defecto, anónima. */
  readonly roles?: readonly string[];
  /** Re-consentimiento pendiente devuelto por el login. */
  readonly pendingPurposes?: readonly string[];
}

export function sessionResponse(roles: readonly string[], pendingPurposes: readonly string[] = []) {
  return {
    accessToken: 'tok-prueba',
    tokenType: 'Bearer',
    expiresIn: 900,
    user: { id: 'u-1', email: 'turista@ejemplo.com', fullName: 'Carlos Restrepo', roles },
    requiresConsentUpdate: pendingPurposes.length > 0,
    pendingPolicyVersion: pendingPurposes.length > 0 ? 'v0.2' : null,
    pendingPurposes,
  };
}

export function renderApp(
  path: string,
  fakeRoutes: FakeRoutes = {},
  options: RenderAppOptions = {},
) {
  const http = createFakeHttp(fakeRoutes);
  const client: HttpClient = { request: http.request };
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, refetchInterval: false } },
  });
  const session = createSessionStore({
    http: client,
    initialStatus: 'anonymous',
    channelName: null,
    locks: undefined,
  });
  if (options.roles)
    session.adopt(sessionResponse(options.roles, options.pendingPurposes) as never);
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  const view = render(
    <AppProviders httpClient={client} session={session} queryClient={queryClient}>
      <RouterProvider router={router} />
      <ConsentGate />
    </AppProviders>,
  );
  return { ...view, router, http, session };
}
