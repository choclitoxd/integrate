/**
 * Proxy de Cloudflare Pages Functions para /api/* (ADR-0024 §3).
 *
 * La PWA llama a /api en su propio origen, igual que con el proxy de Vite en desarrollo, para que la
 * cookie de refresh (HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth) viaje sin CORS. El origen
 * del backend llega en la variable de Pages `API_ORIGIN` (p. ej. https://<servicio>.koyeb.app).
 */

interface Env {
  readonly API_ORIGIN?: string;
}

interface PagesContext {
  readonly request: Request;
  readonly env: Env;
}

const HTTPS_ORIGIN = /^https:\/\/[^\s/]+$/;

// Cabeceras de un salto (RFC 9110 §7.6.1): no se reenvían al backend.
const HOP_BY_HOP = ['connection', 'keep-alive', 'te', 'trailer', 'transfer-encoding', 'upgrade'];

function problem(status: number, title: string, detail: string): Response {
  return new Response(JSON.stringify({ type: 'about:blank', title, status, detail }), {
    status,
    headers: { 'Content-Type': 'application/problem+json', 'Cache-Control': 'no-store' },
  });
}

export async function onRequest({ request, env }: PagesContext): Promise<Response> {
  const origin = env.API_ORIGIN?.trim().replace(/\/+$/, '');
  if (!origin || !HTTPS_ORIGIN.test(origin)) {
    return problem(502, 'Bad Gateway', 'API_ORIGIN no está configurada en Cloudflare Pages.');
  }

  const incoming = new URL(request.url);
  const upstream = new URL(incoming.pathname + incoming.search, origin);

  const headers = new Headers(request.headers);
  headers.delete('host');
  for (const name of HOP_BY_HOP) headers.delete(name);
  const clientIp = request.headers.get('cf-connecting-ip');
  if (clientIp) headers.set('X-Forwarded-For', clientIp);
  headers.set('X-Forwarded-Proto', incoming.protocol.replace(':', ''));
  headers.set('X-Forwarded-Host', incoming.host);

  const hasBody = request.method !== 'GET' && request.method !== 'HEAD';
  try {
    // redirect: 'manual' para que el navegador vea las redirecciones del backend tal cual.
    return await fetch(upstream, {
      method: request.method,
      headers,
      body: hasBody ? request.body : null,
      redirect: 'manual',
    });
  } catch {
    // Koyeb Free suspende el servicio sin tráfico: el primer intento puede fallar mientras despierta.
    return problem(
      502,
      'Bad Gateway',
      'El backend no respondió. Intenta de nuevo en unos segundos.',
    );
  }
}
