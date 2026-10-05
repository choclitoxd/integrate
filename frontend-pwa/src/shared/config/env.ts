/**
 * Configuración de la PWA leída de import.meta.env y validada al arrancar (fail-fast, SEC-DIR-004).
 * Las variables VITE_* se incrustan en el bundle público: aquí nunca hay secretos.
 */
export interface AppConfig {
  /** Base de la API sin barra final: "/api" (proxy de desarrollo) o "https://api.integrate.co". */
  readonly apiBaseUrl: string;
}

export class ConfigError extends Error {
  override readonly name = 'ConfigError';
}

const ABSOLUTE_HTTP_URL = /^https?:\/\/[^\s/]+(\/\S*)?$/;
const RELATIVE_PATH = /^\/[^\s/]\S*$/;

export function parseConfig(env: Partial<Record<string, string | undefined>>): AppConfig {
  const raw = env.VITE_API_BASE_URL?.trim();
  if (!raw) {
    throw new ConfigError(
      'VITE_API_BASE_URL no está configurada. Copia frontend-pwa/.env.example a .env.development.local.',
    );
  }
  if (!ABSOLUTE_HTTP_URL.test(raw) && !RELATIVE_PATH.test(raw)) {
    throw new ConfigError(
      `VITE_API_BASE_URL debe ser una ruta ("/api") o una URL http(s) absoluta; se recibió "${raw}".`,
    );
  }
  return { apiBaseUrl: raw.replace(/\/+$/, '') };
}

let cached: AppConfig | undefined;

export function getConfig(): AppConfig {
  cached ??= parseConfig(import.meta.env);
  return cached;
}
