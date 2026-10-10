import { afterEach, describe, expect, it, vi } from 'vitest';

import { ApiError, NetworkError, type HttpClient } from '@/shared/api';

import { createSessionStore, hasRole } from './sessionStore';

function tokenResponse(token: string, overrides: Record<string, unknown> = {}) {
  return {
    accessToken: token,
    tokenType: 'Bearer',
    expiresIn: 900,
    user: {
      id: 'u-1',
      email: 'turista@ejemplo.com',
      fullName: 'Carlos Restrepo',
      roles: ['ROLE_TOURIST', 'ROLE_FUTURO'],
    },
    requiresConsentUpdate: false,
    pendingPolicyVersion: null,
    pendingPurposes: [],
    ...overrides,
  };
}

const unauthorized = () =>
  new ApiError(
    { type: 'about:blank', title: 'x', status: 401, code: 'AUTH_REFRESH_TOKEN_INVALID' },
    null,
  );

function storeWith(handler: (path: string) => Promise<unknown>) {
  const request = vi.fn((path: string) => handler(path));
  const http: HttpClient = { request: request as HttpClient['request'] };
  const store = createSessionStore({ http, channelName: null, locks: undefined });
  return { store, request };
}

describe('createSessionStore', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('restaura la sesión con la cookie y nunca expone el token en el estado', async () => {
    const { store } = storeWith(() => Promise.resolve(tokenResponse('tok-1')));
    expect(store.getState().status).toBe('checking');
    await store.restore();
    const state = store.getState();
    expect(state.status).toBe('authenticated');
    expect(JSON.stringify(state)).not.toContain('tok-1');
    expect(store.getAccessToken()).toBe('tok-1');
    // Un rol desconocido se descarta sin romper la sesión.
    expect(state.status === 'authenticated' && state.user.roles).toEqual(['ROLE_TOURIST']);
    expect(hasRole(state, 'ROLE_TOURIST')).toBe(true);
    expect(hasRole(state, 'ROLE_LINK_MANAGER')).toBe(false);
  });

  it('un 401 al restaurar deja la sesión anónima', async () => {
    const { store } = storeWith(() => Promise.reject(unauthorized()));
    await store.restore();
    expect(store.getState().status).toBe('anonymous');
    expect(store.getAccessToken()).toBeNull();
  });

  it('comparte una sola renovación entre varias peticiones concurrentes', async () => {
    let release: (value: unknown) => void = () => undefined;
    const { store, request } = storeWith(
      () =>
        new Promise((resolve) => {
          release = resolve;
        }),
    );
    const all = Promise.all([store.refresh(), store.refresh(), store.refresh()]);
    release(tokenResponse('tok-2'));
    expect(await all).toEqual(['tok-2', 'tok-2', 'tok-2']);
    expect(request).toHaveBeenCalledTimes(1);

    // Terminada la renovación, la siguiente vuelve a pedir una.
    void store.refresh();
    expect(request).toHaveBeenCalledTimes(2);
  });

  it('serializa la renovación entre pestañas con navigator.locks', async () => {
    const lockRequest = vi.fn((_name: string, task: () => Promise<unknown>) => task());
    const http: HttpClient = {
      request: (() => Promise.resolve(tokenResponse('tok-3'))) as HttpClient['request'],
    };
    const store = createSessionStore({
      http,
      channelName: null,
      locks: { request: lockRequest } as unknown as LockManager,
    });
    await store.refresh();
    expect(lockRequest).toHaveBeenCalledWith('integrate-auth-refresh', expect.any(Function));
  });

  it('un fallo de red al renovar no cierra una sesión activa', async () => {
    const { store } = storeWith(() => Promise.reject(new NetworkError(new TypeError())));
    store.adopt(tokenResponse('tok-4') as never);
    expect(await store.refresh()).toBeNull();
    expect(store.getState().status).toBe('authenticated');
  });

  it('rechaza una respuesta de sesión malformada', async () => {
    const { store } = storeWith(() => Promise.resolve({ accessToken: 'x' }));
    await expect(store.signIn({ email: 'a@b.co', password: 'x' })).rejects.toThrow(/no es válida/);
    expect(store.getState().status).toBe('checking');
  });

  it('expone el re-consentimiento exigido y lo limpia al completarse', async () => {
    const { store } = storeWith(() =>
      Promise.resolve(
        tokenResponse('tok-5', {
          requiresConsentUpdate: true,
          pendingPolicyVersion: 'v2.0',
          pendingPurposes: ['PERSONAL_DATA'],
        }),
      ),
    );
    await store.signIn({ email: 'a@b.co', password: 'x' });
    const state = store.getState();
    expect(state.status === 'authenticated' && state.consentUpdate).toEqual({
      policyVersion: 'v2.0',
      purposes: ['PERSONAL_DATA'],
    });
    store.consentCompleted();
    const after = store.getState();
    expect(after.status === 'authenticated' && after.consentUpdate).toBeNull();
  });

  it('renueva de forma proactiva 60 s antes de vencer', async () => {
    vi.useFakeTimers();
    const { store, request } = storeWith(() => Promise.resolve(tokenResponse('tok-6')));
    store.adopt(tokenResponse('tok-6') as never);
    await vi.advanceTimersByTimeAsync(839_000);
    expect(request).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(1_000);
    expect(request).toHaveBeenCalledWith('/v1/auth/refresh', { method: 'POST' });
  });

  it('cierra la sesión aunque el logout falle por red', async () => {
    const { store, request } = storeWith((path) =>
      path === '/v1/auth/logout'
        ? Promise.reject(new NetworkError(new TypeError()))
        : Promise.resolve(tokenResponse('tok-7')),
    );
    await store.restore();
    await store.signOut();
    expect(request).toHaveBeenLastCalledWith('/v1/auth/logout', { method: 'POST' });
    expect(store.getState().status).toBe('anonymous');
    expect(store.getAccessToken()).toBeNull();
  });
});
