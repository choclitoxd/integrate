const AUTH_PAGES = ['/ingresar', '/registro'];

/**
 * Destino tras ingresar (`?volver=`). Solo rutas internas: empiezan por "/" y no por "//" ni "/\",
 * que el navegador trataría como otro origen (open redirect, ADR-0013 §5).
 */
export function safeReturnPath(raw: string | null): string {
  if (!raw?.startsWith('/') || raw.startsWith('//') || raw.startsWith('/\\')) return '/';
  if (AUTH_PAGES.some((page) => raw === page || raw.startsWith(`${page}?`))) return '/';
  return raw;
}

/** `?volver=` para enlazar entre login y registro conservando el destino. */
export function returnQuery(path: string): string {
  return path === '/' ? '' : `?volver=${encodeURIComponent(path)}`;
}
