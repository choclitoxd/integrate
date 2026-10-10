import { createContext, useContext } from 'react';

import { ApiError, NetworkError } from './errors';
import type { HttpClient } from './httpClient';

/** Cliente HTTP inyectado por app/providers; las pruebas lo sustituyen por uno simulado. */
export const HttpClientContext = createContext<HttpClient | null>(null);

export function useHttpClient(): HttpClient {
  const client = useContext(HttpClientContext);
  if (!client) throw new Error('useHttpClient requiere <HttpClientContext.Provider>.');
  return client;
}

const MAX_RETRIES = 2;

/**
 * Política de reintento de las consultas (ADR-0012 §2): solo fallos de red y 5xx, hasta dos veces.
 * Un 4xx describe la petición, no la conexión: repetirla no cambia la respuesta.
 */
export function shouldRetryRequest(failureCount: number, error: unknown): boolean {
  if (failureCount >= MAX_RETRIES) return false;
  if (error instanceof NetworkError) return true;
  return error instanceof ApiError && error.status >= 500;
}
