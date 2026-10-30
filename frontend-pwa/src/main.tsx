import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { createBrowserRouter } from 'react-router';
import { RouterProvider } from 'react-router/dom';

import { ConfigErrorScreen } from '@/app/pages/ConfigErrorScreen';
import { AppProviders } from '@/app/providers/AppProviders';
import { routes } from '@/app/routes';
import { ConsentGate } from '@/features/auth';
import { createHttpClient } from '@/shared/api';
import { ConfigError, getConfig } from '@/shared/config';
import { registerServiceWorker, runInactivityMaintenance } from '@/shared/offline';
import { createSessionStore } from '@/shared/session';

import './styles/index.css';

const container = document.getElementById('root');
if (!container) throw new Error('No existe el elemento #root en index.html.');

function bootstrap() {
  let apiBaseUrl: string;
  try {
    apiBaseUrl = getConfig().apiBaseUrl;
  } catch (error) {
    if (error instanceof ConfigError) return <ConfigErrorScreen message={error.message} />;
    throw error;
  }
  // ADR-0013 §7: la sesión usa un cliente sin token para /v1/auth/*; el resto lleva el token en
  // memoria y renueva una sola vez ante un 401.
  const session = createSessionStore({ http: createHttpClient({ baseUrl: apiBaseUrl }) });
  const httpClient = createHttpClient({
    baseUrl: apiBaseUrl,
    getAccessToken: session.getAccessToken,
    onUnauthorized: session.refresh,
  });
  void session.restore();
  // ADR-0036 §3.3 y §5: mantenimiento de caducidad/inactividad en IndexedDB y registro del Service Worker.
  // Si IndexedDB no abre, la app sigue funcionando en línea: el mantenimiento se reintenta en el siguiente arranque.
  runInactivityMaintenance().catch(() => undefined);
  if (import.meta.env.PROD) {
    void registerServiceWorker();
  }
  return (
    <AppProviders httpClient={httpClient} session={session}>
      <RouterProvider router={createBrowserRouter(routes)} />
      <ConsentGate />
    </AppProviders>
  );
}

createRoot(container).render(<StrictMode>{bootstrap()}</StrictMode>);
