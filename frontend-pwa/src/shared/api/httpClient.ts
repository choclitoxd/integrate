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
   * offline_data_indexeddb.md §1); lo cablea el módulo de autenticación en T-1.2.
   */
  readonly getAccessToken?: () => string | null;
  readonly fetchImpl?: typeof fetch;
}

export interface HttpClient {
  request<T>(path: string, options?: RequestOptions): Promise<T>;
}

const TRACE_ID_HEADER = 'X-Trace-Id';

function isJson(contentType: string | null): boolean {
  return contentType !== null && /\bjson\b/i.test(contentType);
}

export function createHttpClient({
  baseUrl,
  getAccessToken,
  fetchImpl = (input, init) => fetch(input, init),
}: HttpClientOptions): HttpClient {
  return {
    async request<T>(path: string, options: RequestOptions = {}): Promise<T> {
      const headers = new Headers({ Accept: 'application/json, application/problem+json' });
      for (const [name, value] of Object.entries(options.headers ?? {})) headers.set(name, value);
      if (options.body !== undefined) headers.set('Content-Type', 'application/json');
      const token = getAccessToken?.();
      if (token) headers.set('Authorization', `Bearer ${token}`);

      let response: Response;
      try {
        response = await fetchImpl(`${baseUrl}${path}`, {
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
