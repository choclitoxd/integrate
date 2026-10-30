/**
 * Registro del Service Worker de la PWA y coordinación de Background Sync (ADR-0036 §5).
 */

export const OFFLINE_SYNC_TAG = 'integrate-offline-sync' as const;

export type SwClientMessage = { readonly type: 'SKIP_WAITING' } | { readonly type: 'TRIGGER_SYNC' };

export interface SwBroadcastMessage {
  readonly type: 'SW_SYNC_REQUESTED';
  readonly tag: string;
}

interface SyncManagerLike {
  register(tag: string): Promise<void>;
}

interface ServiceWorkerRegistrationWithSync extends ServiceWorkerRegistration {
  readonly sync?: SyncManagerLike;
}

/**
 * Registra `/sw.js` en navegadores compatibles (producción o cuando `enableInDev` sea `true`).
 */
export async function registerServiceWorker(
  options: {
    readonly swUrl?: string;
    readonly onSyncRequested?: () => void;
  } = {},
): Promise<ServiceWorkerRegistration | null> {
  if (typeof navigator === 'undefined' || !('serviceWorker' in navigator)) {
    return null;
  }

  const swUrl = options.swUrl ?? '/sw.js';
  try {
    const registration = await navigator.serviceWorker.register(swUrl, { scope: '/' });

    if (options.onSyncRequested) {
      navigator.serviceWorker.addEventListener('message', (event: MessageEvent<unknown>) => {
        const data = event.data as Partial<SwBroadcastMessage> | null;
        if (data?.type === 'SW_SYNC_REQUESTED') {
          options.onSyncRequested?.();
        }
      });
    }

    return registration;
  } catch {
    return null;
  }
}

/**
 * Solicita al navegador programar una tarea de Background Sync (`integrate-offline-sync`).
 * Si la API `SyncManager` no está disponible (p. ej. iOS Safari), devuelve `false` para que el
 * cliente use los disparadores en primer plano (`online` y `visibilitychange`).
 */
export async function requestBackgroundSync(tag: string = OFFLINE_SYNC_TAG): Promise<boolean> {
  if (typeof navigator === 'undefined' || !('serviceWorker' in navigator)) {
    return false;
  }
  try {
    const registration = (await navigator.serviceWorker.ready) as ServiceWorkerRegistrationWithSync;
    if (registration.sync && typeof registration.sync.register === 'function') {
      await registration.sync.register(tag);
      return true;
    }
  } catch {
    return false;
  }
  return false;
}
