import { isRouteErrorResponse, useRouteError } from 'react-router';

import { ApiError } from '@/shared/api';
import { Button } from '@/shared/ui';

/** Error inesperado al renderizar o cargar una ruta. Nunca muestra stacktraces al usuario. */
export function RouteErrorPage() {
  const error = useRouteError();
  const traceId = error instanceof ApiError ? error.traceId : undefined;
  const status = isRouteErrorResponse(error) ? error.status : undefined;

  return (
    <main className="mx-auto max-w-2xl px-4 py-12">
      <title>Error · Intégrate</title>
      <h1 className="text-3xl font-bold text-brand">Algo salió mal</h1>
      <p role="alert" className="mt-4 text-lg text-ink-muted">
        No pudimos mostrar esta página{status ? ` (código ${String(status)})` : ''}. Intenta de
        nuevo.
      </p>
      {traceId && (
        <p className="mt-2 text-sm text-ink-muted">
          Código de seguimiento: <code>{traceId}</code>
        </p>
      )}
      <Button
        className="mt-6"
        onClick={() => {
          window.location.reload();
        }}
      >
        Reintentar
      </Button>
    </main>
  );
}
