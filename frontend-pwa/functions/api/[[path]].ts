/**
 * Proxy de Cloudflare Pages Functions para /api/* (ADR-0024 §3, ADR-0026 §5).
 *
 * La PWA llama a /api en su propio origen, igual que con el proxy de Vite en desarrollo, para que la
 * cookie de refresh (HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth) viaje sin CORS. El origen
 * del backend llega en la variable de Pages `API_ORIGIN` (p. ej. https://<servicio>.snapdeploy.app o la URL del túnel).
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

// SnapDeploy duerme el contenedor sin tráfico y, mientras despierta, responde con su página HTML de espera.
// La API nunca responde HTML (errores en application/problem+json), así que un text/html es «dormido».
// SnapDeploy solo despierta el contenedor cuando una PERSONA abre su URL en un navegador: una llamada de
// servidor como esta recibe el 503 pero no lo despierta, así que el mensaje indica abrir la URL a mano.
const WAKE_RETRY_SECONDS = '60';

function isWakePage(response: Response): boolean {
  return (response.headers.get('content-type') ?? '').toLowerCase().startsWith('text/html');
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
  // Las cabeceras cf-* describen la petición entrante; reenviarlas hace que Cloudflare rechace (403) el
  // salto hacia un túnel o dominio propio. La IP del cliente viaja en X-Forwarded-For.
  for (const name of [...headers.keys()]) if (name.startsWith('cf-')) headers.delete(name);
  if (clientIp) headers.set('X-Forwarded-For', clientIp);
  headers.set('X-Forwarded-Proto', incoming.protocol.replace(':', ''));
  headers.set('X-Forwarded-Host', incoming.host);

  const hasBody = request.method !== 'GET' && request.method !== 'HEAD';
  // El cuerpo de un POST/PUT es un stream que solo se puede enviar una vez: solo se reintenta sin cuerpo.
  const attempts = hasBody ? 1 : 2;
  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    try {
      // redirect: 'manual' para que el navegador vea las redirecciones del backend tal cual.
      const response = await fetch(upstream, {
        method: request.method,
        headers,
        body: hasBody ? request.body : null,
        redirect: 'manual',
      });
      if (isWakePage(response)) {
        await response.body?.cancel();
        const woken = problem(
          503,
          'Service Unavailable',
          `El backend está dormido. Ábrelo una vez en el navegador (${origin}), espera un minuto y vuelve a intentar.`,
        );
        woken.headers.set('Retry-After', WAKE_RETRY_SECONDS);
        return woken;
      }
      return response;
    } catch {
      // Un fallo de red puntual (p. ej. el contenedor aún enrutando) se reintenta una vez.
    }
  }
  return problem(502, 'Bad Gateway', 'El backend no respondió. Intenta de nuevo en unos segundos.');
}
