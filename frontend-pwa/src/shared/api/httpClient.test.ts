import { describe, expect, it, vi } from 'vitest';

import { ApiError, NetworkError } from './errors';
import { createHttpClient } from './httpClient';

function jsonResponse(body: unknown, init: { status?: number; contentType?: string } = {}) {
  const { status = 200, contentType = 'application/json' } = init;
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': contentType } });
}

function clientReturning(response: Response | Error, getAccessToken?: () => string | null) {
  const fetchImpl = vi.fn<typeof fetch>(() =>
    response instanceof Error ? Promise.reject(response) : Promise.resolve(response),
  );
  const client = createHttpClient({
    baseUrl: '/api',
    fetchImpl,
    ...(getAccessToken && { getAccessToken }),
  });
  return { client, fetchImpl };
}

function lastRequest(fetchImpl: ReturnType<typeof vi.fn<typeof fetch>>) {
  const [url, init] = fetchImpl.mock.calls[0] ?? [];
  return { url, init: init ?? {}, headers: new Headers(init?.headers) };
}

describe('createHttpClient', () => {
  it('envía cookies, Accept y JSON, y devuelve el DTO sin sobre', async () => {
    const { client, fetchImpl } = clientReturning(jsonResponse({ id: 'b-1' }, { status: 201 }));

    const result = await client.request<{ id: string }>('/v1/bookings', {
      method: 'POST',
      body: { slots: 2 },
    });

    expect(result).toEqual({ id: 'b-1' });
    const { url, init, headers } = lastRequest(fetchImpl);
    expect(url).toBe('/api/v1/bookings');
    expect(init.credentials).toBe('include');
    expect(init.body).toBe('{"slots":2}');
    expect(headers.get('Content-Type')).toBe('application/json');
    expect(headers.get('Accept')).toContain('application/problem+json');
    expect(headers.has('Authorization')).toBe(false);
  });

  it('añade el Bearer token en memoria cuando existe', async () => {
    const { client, fetchImpl } = clientReturning(jsonResponse({}), () => 'tok-123');
    await client.request('/v1/me');
    expect(lastRequest(fetchImpl).headers.get('Authorization')).toBe('Bearer tok-123');
  });

  it('devuelve undefined en 204', async () => {
    const { client } = clientReturning(new Response(null, { status: 204 }));
    await expect(client.request('/v1/x', { method: 'DELETE' })).resolves.toBeUndefined();
  });

  it('traduce problem+json (RFC 9457) a ApiError con code, traceId e invalidParams', async () => {
    const problem = {
      type: 'https://api.integrate.co/problems/validation-failed',
      title: 'Datos de entrada inválidos',
      status: 422,
      code: 'VALIDATION_FAILED',
      traceId: 'trace-body-1',
      invalidParams: [{ name: 'participants[1].docNumber', reason: 'requerido' }, { bad: true }],
    };
    const { client } = clientReturning(
      jsonResponse(problem, { status: 422, contentType: 'application/problem+json' }),
    );

    const error = await client.request('/v1/bookings').catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    const apiError = error as ApiError;
    expect(apiError.status).toBe(422);
    expect(apiError.code).toBe('VALIDATION_FAILED');
    expect(apiError.traceId).toBe('trace-body-1');
    expect(apiError.invalidParams).toEqual([
      { name: 'participants[1].docNumber', reason: 'requerido' },
    ]);
    expect(apiError.message).toBe('Datos de entrada inválidos');
  });

  it('usa X-Trace-Id y el statusText cuando el error no trae cuerpo JSON', async () => {
    const { client } = clientReturning(
      new Response('', {
        status: 401,
        statusText: 'Unauthorized',
        headers: { 'X-Trace-Id': 'hdr-9' },
      }),
    );

    const error = (await client.request('/v1/me').catch((e: unknown) => e)) as ApiError;

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(401);
    expect(error.traceId).toBe('hdr-9');
    expect(error.code).toBeUndefined();
    expect(error.problem.type).toBe('about:blank');
    expect(error.problem.title).toBe('Unauthorized');
  });

  it('envuelve los fallos de transporte en NetworkError', async () => {
    const { client } = clientReturning(new TypeError('Failed to fetch'));
    await expect(client.request('/v1/x')).rejects.toBeInstanceOf(NetworkError);
  });
});
