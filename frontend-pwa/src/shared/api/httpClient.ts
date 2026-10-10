import { ApiError, NetworkError, toProblemDetail } from './errors';

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

export interface RequestOptions {
  readonly method?: HttpMethod;
  /** Se serializa como JSON. */
  readonly body?: unknown;
  readonly headers?: Readonly<Record<string, string>>;
  readonly signal?: AbortSignal;
}

export interface HttpClientOptions {
  /** Base sin barra final, p. ej. "/api" o "https://api.integrate.co". */
  readonly baseUrl: string;
  /**
   * Devuelve el access token vigente. Vive solo en memoria (nunca localStorage/IndexedDB,
   * offline_data_indexeddb.md §1); lo cablea la PWA en T-1.11 sobre los endpoints de T-1.2.
   */
  readonly getAccessToken?: () => string | null;
  /**
   * Renueva la sesión tras un 401 de una petición que llevaba token (ADR-0013 §3). Devuelve el token
   * nuevo o null; la petición se reintenta una sola vez. Nunca se invoca para /v1/auth/*.
   */
  readonly onUnauthorized?: () => Promise<string | null>;
  readonly fetchImpl?: typeof fetch;
}

export interface HttpClient {
  request<T>(path: string, options?: RequestOptions): Promise<T>;
}

const TRACE_ID_HEADER = 'X-Trace-Id';

/**
 * Un 401 de /v1/auth/* es la respuesta del propio flujo de credenciales (activación, login): renovar y
 * reintentar gastaría intentos. La excepción es el enrolamiento asistido, que va con el token del gestor.
 */
function renewsOnUnauthorized(path: string): boolean {
  return !path.startsWith('/v1/auth/') || path === '/v1/auth/register-assisted';
}

function isJson(contentType: string | null): boolean {
  return contentType !== null && /\bjson\b/i.test(contentType);
}

export function createHttpClient({
  baseUrl,
  getAccessToken,
  onUnauthorized,
  fetchImpl = (input, init) => fetch(input, init),
}: HttpClientOptions): HttpClient {
  async function send(path: string, options: RequestOptions, token: string | null | undefined) {
    const headers = new Headers({ Accept: 'application/json, application/problem+json' });
    for (const [name, value] of Object.entries(options.headers ?? {})) headers.set(name, value);
    if (options.body !== undefined) headers.set('Content-Type', 'application/json');
    if (token) headers.set('Authorization', `Bearer ${token}`);
    try {
      return await fetchImpl(`${baseUrl}${path}`, {
        method: options.method ?? 'GET',
        headers,
        // La cookie de refresh (HttpOnly; SameSite=Strict; Path=/api/v1/auth) debe viajar.
        credentials: 'include',
        ...(options.body !== undefined && { body: JSON.stringify(options.body) }),
        ...(options.signal && { signal: options.signal }),
      });
    } catch (cause) {
      throw new NetworkError(cause);
    }
  }

  return {
    async request<T>(path: string, options: RequestOptions = {}): Promise<T> {
      const token = getAccessToken?.();
      let response = await send(path, options, token);

      // Token vencido o revocado: una renovación compartida y un único reintento.
      if (response.status === 401 && token && onUnauthorized && renewsOnUnauthorized(path)) {
        const renewed = await onUnauthorized();
        if (renewed) response = await send(path, options, renewed);
      }

      const contentType = response.headers.get('Content-Type');
      if (!response.ok) {
        const body: unknown = isJson(contentType) ? await response.json().catch(() => null) : null;
        throw new ApiError(
          toProblemDetail(body, response.status, response.statusText),
          response.headers.get(TRACE_ID_HEADER),
        );
      }
      if (response.status === 204 || !isJson(contentType)) return undefined as T;
      return (await response.json()) as T;
    },
  };
}
