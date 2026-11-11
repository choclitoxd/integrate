import { describe, expect, it, vi } from 'vitest';
import { createHttpClient } from './httpClient';

describe('rutas de cuenta propia', () => {
  it.each(['/v1/auth/me', '/v1/auth/me/password'])('renueva un 401 en %s', async (path) => {
    const fetchImpl = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    const renew = vi.fn().mockResolvedValue('nuevo');
    const http = createHttpClient({
      baseUrl: '/api',
      getAccessToken: () => 'anterior',
      onUnauthorized: renew,
      fetchImpl,
    });
    await http.request(path, { method: 'POST' });
    expect(renew).toHaveBeenCalledTimes(1);
    expect(fetchImpl).toHaveBeenCalledTimes(2);
  });
  it('no reintenta una contraseña actual incorrecta con 422', async () => {
    const fetchImpl = vi.fn<typeof fetch>().mockResolvedValue(new Response(null, { status: 422 }));
    const renew = vi.fn();
    const http = createHttpClient({
      baseUrl: '/api',
      getAccessToken: () => 'anterior',
      onUnauthorized: renew,
      fetchImpl,
    });
    await expect(http.request('/v1/auth/me/password', { method: 'POST' })).rejects.toMatchObject({
      status: 422,
    });
    expect(renew).not.toHaveBeenCalled();
    expect(fetchImpl).toHaveBeenCalledTimes(1);
  });
});
