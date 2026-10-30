/**
 * Service Worker de Intégrate (Workbox, ADR-0036 §5).
 * - Precache del App Shell (`index.html`, JS/CSS, iconos y `manifest.webmanifest`).
 * - Navigation Fallback a `/index.html` para abrir `/gestor` y rutas SPA en modo avión.
 * - `NetworkOnly` estricto en endpoints autenticados, financieros y offline (`/api/v1/{auth,offline,bookings,payments,gestor,admin}/**`).
 * - `NetworkFirst` acotado (timeout 3 s, máx. 30 entradas, TTL 72 h) en `/api/v1/catalog/**` y `/api/v1/privacy-policies/current`.
 * - Coordinación de Background Sync (`integrate-offline-sync`) para T-2.13.
 */
import { clientsClaim, type WorkboxPlugin } from 'workbox-core';
import { ExpirationPlugin } from 'workbox-expiration';
import {
  cleanupOutdatedCaches,
  createHandlerBoundToURL,
  precacheAndRoute,
  type PrecacheEntry,
} from 'workbox-precaching';
import { NavigationRoute, registerRoute } from 'workbox-routing';
import { NetworkFirst, NetworkOnly } from 'workbox-strategies';

import {
  OFFLINE_SYNC_TAG,
  type SwBroadcastMessage,
  type SwClientMessage,
} from './shared/offline/registerSw';

interface SyncEventLike extends Event {
  readonly tag: string;
  waitUntil(promise: Promise<unknown>): void;
}

interface ExtendableMessageEventLike extends Event {
  readonly data: unknown;
}

interface SwClientLike {
  postMessage(message: SwBroadcastMessage): void;
}

/**
 * Manifiesto de precache que `vite.config.ts` (`buildServiceWorkerPlugin`) sustituye por un literal al compilar este
 * archivo. Es un identificador propio y se lee directamente: `define` solo reemplaza la expresión exacta, así que un
 * alias como `self.__WB_MANIFEST` leído por otra variable quedaba sin sustituir y el Service Worker no precacheaba
 * nada. En desarrollo y en pruebas no existe y el Service Worker no se inicializa.
 */
declare const __PRECACHE_MANIFEST__: readonly (PrecacheEntry | string)[] | undefined;

interface SwGlobalScope {
  skipWaiting(): Promise<void>;
  clients: {
    matchAll(options?: {
      type?: 'window';
      includeUncontrolled?: boolean;
    }): Promise<readonly SwClientLike[]>;
  };
  addEventListener(type: 'sync', listener: (event: SyncEventLike) => void): void;
  addEventListener(type: 'message', listener: (event: ExtendableMessageEventLike) => void): void;
}

const swSelf = self as unknown as SwGlobalScope;

/** Rutas de API que jamás deben almacenarse en Cache Storage del navegador (FE-DIR-001 D4, ADR-0036 §5.2). */
const NETWORK_ONLY_API_PREFIXES = [
  '/api/v1/auth/',
  '/api/v1/offline/',
  '/api/v1/bookings',
  '/api/v1/me/',
  '/api/v1/payments/',
  '/api/v1/gestor/',
  '/api/v1/admin/',
  '/api/v1/host/',
  '/api/v1/dev/',
] as const;

export function isSensitiveApiPath(pathname: string): boolean {
  return NETWORK_ONLY_API_PREFIXES.some((prefix) => pathname.startsWith(prefix));
}

export function isPublicCatalogCacheablePath(pathname: string): boolean {
  return (
    pathname.startsWith('/api/v1/catalog/') ||
    pathname.startsWith('/api/v1/experiences') ||
    pathname === '/api/v1/privacy-policies/current'
  );
}

export function isNavigationFallbackAllowed(pathname: string): boolean {
  return !pathname.startsWith('/api/') && !pathname.startsWith('/manual');
}

export function initServiceWorker(manifestEntries: readonly (PrecacheEntry | string)[]): void {
  cleanupOutdatedCaches();
  clientsClaim();

  precacheAndRoute([...manifestEntries]);

  try {
    const navigationHandler = createHandlerBoundToURL('/index.html');
    registerRoute(
      new NavigationRoute(navigationHandler, {
        denylist: [/^\/api\//, /^\/manual(?:\/|$)/],
      }),
    );
  } catch {
    // En pruebas unitarias sin `/index.html` precacheado no se registra el fallback de navegación.
  }

  // Endpoints transaccionales, de sesión y de sincronización offline: estrictamente NetworkOnly.
  registerRoute(({ url }) => isSensitiveApiPath(url.pathname), new NetworkOnly());

  // Referencias públicas de catálogo y política vigente: NetworkFirst con TTL de 72 h.
  registerRoute(
    ({ url, request }) => request.method === 'GET' && isPublicCatalogCacheablePath(url.pathname),
    new NetworkFirst({
      cacheName: 'integrate-public-catalog-v1',
      networkTimeoutSeconds: 3,
      plugins: [
        new ExpirationPlugin({
          maxEntries: 30,
          maxAgeSeconds: 72 * 60 * 60,
        }) as unknown as WorkboxPlugin,
      ],
    }),
  );

  swSelf.addEventListener('message', (event: ExtendableMessageEventLike) => {
    const payload = event.data as Partial<SwClientMessage> | null;
    if (payload?.type === 'SKIP_WAITING') {
      void swSelf.skipWaiting();
    }
  });

  swSelf.addEventListener('sync', (event: SyncEventLike) => {
    if (event.tag === OFFLINE_SYNC_TAG) {
      event.waitUntil(
        swSelf.clients
          .matchAll({ type: 'window', includeUncontrolled: true })
          .then((windowClients) => {
            for (const client of windowClients) {
              client.postMessage({
                type: 'SW_SYNC_REQUESTED',
                tag: event.tag,
              });
            }
          }),
      );
    }
  });
}

// Inicialización automática cuando corre como Service Worker real con el manifiesto inyectado.
if (typeof __PRECACHE_MANIFEST__ !== 'undefined') {
  initServiceWorker(__PRECACHE_MANIFEST__);
}
