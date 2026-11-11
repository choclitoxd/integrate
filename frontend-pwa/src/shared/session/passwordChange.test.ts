import { describe, expect, it, vi } from 'vitest';
import { createSessionStore } from './sessionStore';
import type { HttpClient } from '@/shared/api';

describe('cierre después de cambiar contraseña', () => {
  it('impide recuperar una sesión por una renovación en vuelo incluso si falla la limpieza', async () => {
    let release: (value: unknown) => void = () => undefined;
    const request = vi.fn(
      () =>
        new Promise<unknown>((resolve) => {
          release = resolve;
        }),
    );
    const http: HttpClient = { request: request as HttpClient['request'] };
    const store = createSessionStore({
      http,
      channelName: null,
      locks: undefined,
      onBeforeSignOut: () => Promise.reject(new Error('pendiente')),
    });
    const refresh = store.refresh();
    expect(await store.finishPasswordChange()).toBe(false);
    release({
      accessToken: 'token',
      tokenType: 'Bearer',
      expiresIn: 900,
      user: { id: 'u', email: 'a@b.co', fullName: 'Nombre', roles: ['ROLE_TOURIST'] },
      requiresConsentUpdate: false,
      pendingPolicyVersion: null,
      pendingPurposes: [],
    });
    expect(await refresh).toBeNull();
    expect(store.getState().status).toBe('anonymous');
    expect(store.getAccessToken()).toBeNull();
  });
});
