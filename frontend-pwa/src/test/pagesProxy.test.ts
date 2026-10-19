// Las Pages Functions convierten cada archivo de functions/ en una ruta: su prueba vive aquí, no allí.
import { afterEach, describe, expect, it, vi } from 'vitest';

import { onRequest } from '../../functions/api/[[path]]';

const env = { API_ORIGIN: 'https://svc.snapdeploy.app/' };

function call(path: string, init?: RequestInit, proxyEnv: { API_ORIGIN?: string } = env) {
  return onRequest({ request: new Request(`https://app.pages.dev${path}`, init), env: proxyEnv });
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('proxy /api (ADR-0024 §3, ADR-0026 §5)', () => {
  it('reenvía ruta, query y cabeceras sin cf-* ni host', async () => {
    const fetchMock = vi.fn(() => Promise.resolve(new Response('{"ok":true}')));
    vi.stubGlobal('fetch', fetchMock);

    const response = await call('/api/v1/experiences?q=a%20b', {
      headers: {
        cookie: 'x=1',
        host: 'app.pages.dev',
        'cf-connecting-ip': '1.2.3.4',
        'cf-ray': 'r',
      },
    });

    expect(response.status).toBe(200);
    const [url, init] = fetchMock.mock.calls[0] as unknown as [URL, RequestInit];
    expect(String(url)).toBe('https://svc.snapdeploy.app/api/v1/experiences?q=a%20b');
    const headers = init.headers as Headers;
    expect(headers.get('cookie')).toBe('x=1');
    expect(headers.get('host')).toBeNull();
    expect(headers.get('cf-connecting-ip')).toBeNull();
    expect(headers.get('cf-ray')).toBeNull();
    expect(headers.get('x-forwarded-for')).toBe('1.2.3.4');
    expect(headers.get('x-forwarded-proto')).toBe('https');
  });

  it('conserva Set-Cookie del backend', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() =>
        Promise.resolve(
          new Response('{}', {
            headers: { 'set-cookie': 'r=1; HttpOnly; Secure; SameSite=Strict' },
          }),
        ),
      ),
    );
    const response = await call('/api/v1/auth/refresh', { method: 'POST', body: '{}' });
    expect(response.headers.get('set-cookie')).toContain('SameSite=Strict');
  });

  it('convierte la página HTML de espera en un 503 problem+json con Retry-After', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() =>
        Promise.resolve(
          new Response('<html>waking</html>', { headers: { 'content-type': 'text/html' } }),
        ),
      ),
    );
    const response = await call('/api/v1/experiences');
    expect(response.status).toBe(503);
    expect(response.headers.get('content-type')).toBe('application/problem+json');
    expect(response.headers.get('retry-after')).toBe('60');
    const body = (await response.json()) as { detail: string };
    expect(body.detail).toContain('https://svc.snapdeploy.app');
  });

  it('reintenta una vez un GET tras un fallo de red', async () => {
    const fetchMock = vi
      .fn<() => Promise<Response>>()
      .mockRejectedValueOnce(new TypeError('network'))
      .mockResolvedValueOnce(new Response('{}'));
    vi.stubGlobal('fetch', fetchMock);
    const response = await call('/api/v1/experiences');
    expect(response.status).toBe(200);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('no reintenta un POST (el cuerpo solo se puede enviar una vez)', async () => {
    const fetchMock = vi.fn<() => Promise<Response>>().mockRejectedValue(new TypeError('network'));
    vi.stubGlobal('fetch', fetchMock);
    const response = await call('/api/v1/auth/login', { method: 'POST', body: '{}' });
    expect(response.status).toBe(502);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('devuelve 502 si el backend sigue caído tras el reintento', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn<() => Promise<Response>>().mockRejectedValue(new TypeError('network')),
    );
    expect((await call('/api/v1/experiences')).status).toBe(502);
  });

  it.each([
    {},
    { API_ORIGIN: '' },
    { API_ORIGIN: 'http://x.snapdeploy.app' },
    { API_ORIGIN: 'https://x.snapdeploy.app/path' },
  ])('responde 502 si API_ORIGIN no es válida (%j)', async (invalidEnv) => {
    const response = await call('/api/v1/experiences', undefined, invalidEnv);
    expect(response.status).toBe(502);
    expect(response.headers.get('content-type')).toBe('application/problem+json');
  });
});
