import { useEffect } from 'react';

import { ApiError, NetworkError, useHttpClient, type HttpClient } from '@/shared/api';
import {
  getOfflineDB,
  getOrCreateDeviceId,
  notifyOfflineChanges,
  touchOfflineActivity,
  updateOfflineMeta,
  type OfflineRosterEntry,
  type OfflineValidationChannel,
  type OfflineValidationEntry,
  type SyncOutboxEntry,
} from '@/shared/offline';

import { postOfflineSyncBatch, type SyncBatchValidationItemRequest } from './api';

export const SYNC_MAX_BATCH_SIZE = 100;
export const SYNC_BASE_DELAY_MS = 2_000;
export const SYNC_MAX_DELAY_MS = 300_000;
export const SYNC_MAX_RETRIES = 5;

export interface FlushSyncResult {
  readonly dispatched: number;
  readonly confirmed: number;
  readonly conflicts: number;
  readonly rejected: number;
  readonly retried: number;
  readonly deadLettered: number;
  readonly syncBatchId?: string;
}

export interface LocalValidationQueueItem {
  readonly validation: OfflineValidationEntry;
  readonly outbox?: SyncOutboxEntry;
  readonly roster?: OfflineRosterEntry;
}

/**
 * Calcula la demora de retroceso exponencial con jitter de ±20 % para reintentos de lote:
 * `min(300 000 ms, 2 000 ms × 2^reintentos) × [0,8; 1,2]` (ADR-0037 §5.3, RNF-19).
 */
export function computeBackoffDelayMs(
  retryCount: number,
  randomFn: () => number = Math.random,
): number {
  const exponential = Math.min(SYNC_MAX_DELAY_MS, SYNC_BASE_DELAY_MS * 2 ** retryCount);
  return Math.round(exponential * (0.8 + randomFn() * 0.4));
}

/**
 * Cómo se trata un fallo del envío (ADR-0037 §5.3):
 * - `PERMANENT`: el servidor rechazó el lote de forma explícita (400, 403, 409, 422); reintentar no lo arregla.
 * - `NETWORK`: sin cobertura. Nunca agota los reintentos: la cola espera hasta que haya señal, porque el servidor es
 *   idempotente por `syncBatchId` y por `localValidationId` y reenviar es seguro.
 * - `TRANSIENT`: cualquier otro fallo (5xx, 401 mientras se renueva la sesión, 408, 429, una respuesta ilegible o un
 *   error inesperado). Se reintenta con retroceso y solo se agota tras `SYNC_MAX_RETRIES` intentos.
 */
export type SyncFailureKind = 'PERMANENT' | 'NETWORK' | 'TRANSIENT';

const PERMANENT_STATUSES: readonly number[] = [400, 403, 409, 422];

export function classifySyncFailure(error: unknown): SyncFailureKind {
  if (error instanceof NetworkError) return 'NETWORK';
  if (error instanceof ApiError && PERMANENT_STATUSES.includes(error.status)) return 'PERMANENT';
  return 'TRANSIENT';
}

let inMemorySyncLock: Promise<unknown> = Promise.resolve();

async function withExclusiveSyncLock<T>(task: () => Promise<T>): Promise<T> {
  const locks = (
    globalThis.navigator as {
      locks?: {
        request: <R>(
          name: string,
          options: { mode: 'exclusive' },
          cb: () => Promise<R>,
        ) => Promise<R>;
      };
    }
  ).locks;
  if (locks) {
    return locks.request('integrate-offline-sync', { mode: 'exclusive' }, task);
  }
  const next = inMemorySyncLock.then(task, task);
  inMemorySyncLock = next.then(
    () => undefined,
    () => undefined,
  );
  return next;
}

interface OutboxValidationPayload {
  readonly localValidationId: string;
  readonly voucherId: string;
  readonly shortCode: string;
  readonly channel: OfflineValidationChannel;
  readonly signatureSha256: string | null;
  readonly scannedAt: string;
}

function readOutboxPayload(entry: SyncOutboxEntry): OutboxValidationPayload {
  const p = entry.payload;
  const channel: OfflineValidationChannel =
    p.channel === 'MANUAL_CODE' ? 'MANUAL_CODE' : 'OFFLINE_PWA';
  return {
    localValidationId:
      typeof p.localValidationId === 'string' ? p.localValidationId : entry.aggregateId,
    voucherId: typeof p.voucherId === 'string' ? p.voucherId : '',
    shortCode: typeof p.shortCode === 'string' ? p.shortCode : '',
    channel,
    signatureSha256: typeof p.signatureSha256 === 'string' ? p.signatureSha256 : null,
    scannedAt: typeof p.scannedAt === 'string' ? p.scannedAt : entry.createdAt,
  };
}

/**
 * Drena la cola `sync_outbox` enviando un lote idempotente a `POST /api/v1/offline/sync`
 * (T-2.13, ADR-0033 §2, ADR-0037 §5, RNF-19).
 */
export async function flushSyncOutbox(
  http: HttpClient,
  options: {
    readonly force?: boolean;
    readonly nowMs?: number;
    readonly randomFn?: () => number;
  } = {},
): Promise<FlushSyncResult> {
  return withExclusiveSyncLock(async () => {
    const nowMs = options.nowMs ?? Date.now();
    const nowIso = new Date(nowMs).toISOString();
    const db = await getOfflineDB();

    const orderedOutbox = await db.getAllFromIndex('sync_outbox', 'idx_outbox_order');
    const eligible = orderedOutbox
      .filter(
        (entry) =>
          (entry.status === 'QUEUED' ||
            entry.status === 'PROCESSING' ||
            entry.status === 'FAILED_RETRYABLE') &&
          (options.force === true || (entry.nextRetryAt ?? 0) <= nowMs),
      )
      .slice(0, SYNC_MAX_BATCH_SIZE);

    if (eligible.length === 0) {
      return {
        dispatched: 0,
        confirmed: 0,
        conflicts: 0,
        rejected: 0,
        retried: 0,
        deadLettered: 0,
      };
    }

    const firstBatchId = eligible[0]?.syncBatchId;
    const canReuseBatchId =
      typeof firstBatchId === 'string' &&
      firstBatchId.length > 0 &&
      eligible.every((entry) => entry.syncBatchId === firstBatchId);
    const syncBatchId = canReuseBatchId ? firstBatchId : crypto.randomUUID();

    {
      const markTx = db.transaction('sync_outbox', 'readwrite');
      for (const entry of eligible) {
        await markTx.store.put({
          ...entry,
          status: 'PROCESSING',
          syncBatchId,
          updatedAt: nowIso,
        });
      }
      await markTx.done;
    }

    const validationsPayload: SyncBatchValidationItemRequest[] = eligible.map((entry) => {
      const data = readOutboxPayload(entry);
      return {
        localValidationId: data.localValidationId,
        voucherId: data.voucherId,
        scannedAt: data.scannedAt,
        ...(data.signatureSha256 !== null && { signatureHash: data.signatureSha256 }),
        shortCode: data.shortCode,
        channel: data.channel,
      };
    });

    try {
      const response = await postOfflineSyncBatch(http, {
        syncBatchId,
        deviceId: getOrCreateDeviceId(),
        deviceSentAt: nowIso,
        validations: validationsPayload,
      });

      updateOfflineMeta({
        clockSkewSeconds: response.clockSkewSeconds,
        lastSyncedAt: response.processedAt,
      });

      const resultsByLocalId = new Map(
        response.items.map((item) => [item.localValidationId, item] as const),
      );

      const applyTx = db.transaction(['local_validations', 'sync_outbox'], 'readwrite');
      const validationsStore = applyTx.objectStore('local_validations');
      const outboxStore = applyTx.objectStore('sync_outbox');

      for (const entry of eligible) {
        const localValidationId = entry.aggregateId;
        const currentValidation = await validationsStore.get(localValidationId);
        const itemResult = resultsByLocalId.get(localValidationId);

        if (currentValidation && itemResult) {
          const mappedStatus =
            itemResult.status === 'CONFIRMED'
              ? 'SYNCED'
              : itemResult.status === 'CONFLICT'
                ? 'CONFLICT'
                : 'REJECTED';

          await validationsStore.put({
            ...currentValidation,
            syncStatus: mappedStatus,
            serverOutcome: itemResult.serverOutcome,
            incidentId: itemResult.incidentId,
            syncMessage: itemResult.message,
            syncedAt: response.processedAt,
          });
        } else if (currentValidation) {
          await validationsStore.put({
            ...currentValidation,
            syncStatus: 'SYNCED',
            syncedAt: response.processedAt,
          });
        }

        await outboxStore.delete(entry.mutationId);
      }

      await applyTx.done;
      touchOfflineActivity();
      notifyOfflineChanges();

      return {
        dispatched: eligible.length,
        confirmed: response.summary.confirmed,
        conflicts: response.summary.conflicts,
        rejected: response.summary.rejected,
        retried: 0,
        deadLettered: 0,
        syncBatchId,
      };
    } catch (err) {
      const failure = classifySyncFailure(err);

      const errorMessage =
        err instanceof Error ? err.message : 'Fallo de red al sincronizar el lote de asistencias.';

      let retried = 0;
      let deadLettered = 0;

      const errTx = db.transaction(['local_validations', 'sync_outbox'], 'readwrite');
      const validationsStore = errTx.objectStore('local_validations');
      const outboxStore = errTx.objectStore('sync_outbox');

      for (const entry of eligible) {
        // Sin cobertura no se gasta ningún intento: la espera la corta el evento `online`, no un contador.
        const nextRetries = failure === 'NETWORK' ? entry.retryCount : entry.retryCount + 1;
        const exhausted = failure === 'PERMANENT' || nextRetries >= SYNC_MAX_RETRIES;

        if (exhausted) {
          deadLettered += 1;
          await outboxStore.put({
            ...entry,
            retryCount: nextRetries,
            status: 'DEAD_LETTER',
            syncBatchId,
            lastError: errorMessage,
            updatedAt: nowIso,
          });
          const currentValidation = await validationsStore.get(entry.aggregateId);
          if (currentValidation) {
            // Solo un rechazo explícito del servidor marca la validación como rechazada; si se agotaron los
            // intentos por fallos transitorios el servidor puede incluso haberla aceptado: queda pendiente.
            await validationsStore.put({
              ...currentValidation,
              ...(failure === 'PERMANENT' && { syncStatus: 'REJECTED' as const }),
              syncMessage: errorMessage,
            });
          }
        } else {
          retried += 1;
          const delayMs = computeBackoffDelayMs(
            failure === 'NETWORK' ? 0 : entry.retryCount,
            options.randomFn,
          );
          await outboxStore.put({
            ...entry,
            retryCount: nextRetries,
            nextRetryAt: nowMs + delayMs,
            status: 'FAILED_RETRYABLE',
            syncBatchId,
            lastError: errorMessage,
            updatedAt: nowIso,
          });
        }
      }

      await errTx.done;
      notifyOfflineChanges();

      return {
        dispatched: eligible.length,
        confirmed: 0,
        conflicts: 0,
        rejected: 0,
        retried,
        deadLettered,
        syncBatchId,
      };
    }
  });
}

/**
 * Devuelve a la cola las asistencias en `DEAD_LETTER` para reintentarlas (botón «Reintentar» de `/gestor/cola`).
 * Reinicia los intentos, quita el `syncBatchId` (un 409 por contenido distinto no se arregla reenviando el mismo lote)
 * y deja la validación local otra vez pendiente. El servidor es idempotente por `localValidationId`: si alguna ya
 * se había aceptado, el reenvío devuelve su resultado guardado.
 *
 * @returns cuántas asistencias volvieron a la cola
 */
export async function requeueDeadLetters(nowMs: number = Date.now()): Promise<number> {
  const db = await getOfflineDB();
  const nowIso = new Date(nowMs).toISOString();
  const tx = db.transaction(['sync_outbox', 'local_validations'], 'readwrite');
  const outboxStore = tx.objectStore('sync_outbox');
  const validationsStore = tx.objectStore('local_validations');
  const dead = await outboxStore.index('idx_outbox_status').getAll('DEAD_LETTER');
  for (const entry of dead) {
    await outboxStore.put({
      ...entry,
      status: 'QUEUED',
      retryCount: 0,
      nextRetryAt: 0,
      syncBatchId: null,
      lastError: null,
      updatedAt: nowIso,
    });
    const validation = await validationsStore.get(entry.aggregateId);
    if (validation?.syncStatus === 'REJECTED') {
      await validationsStore.put({ ...validation, syncStatus: 'PENDING', syncMessage: null });
    }
  }
  await tx.done;
  if (dead.length > 0) notifyOfflineChanges();
  return dead.length;
}

/**
 * Consulta la lista combinada de validaciones locales (`local_validations`), su estado en `sync_outbox`
 * y los datos del titular en `operational_roster` para la vista de cola (`/gestor/cola`).
 */
export async function listLocalValidationsWithOutbox(): Promise<
  readonly LocalValidationQueueItem[]
> {
  const db = await getOfflineDB();
  const [validations, outboxEntries, rosterEntries] = await Promise.all([
    db.getAll('local_validations'),
    db.getAll('sync_outbox'),
    db.getAll('operational_roster'),
  ]);

  const outboxByLocalId = new Map(
    outboxEntries.map((entry) => [entry.aggregateId, entry] as const),
  );
  const rosterByJti = new Map(rosterEntries.map((entry) => [entry.jti, entry] as const));

  return [...validations]
    .sort((a, b) => b.scannedAt.localeCompare(a.scannedAt))
    .map((validation) => {
      const outbox = outboxByLocalId.get(validation.localValidationId);
      const roster = rosterByJti.get(validation.voucherId);
      return {
        validation,
        ...(outbox !== undefined && { outbox }),
        ...(roster !== undefined && { roster }),
      };
    });
}

/**
 * Hook que escucha eventos de reconexión (`online`), retorno al primer plano (`visibilitychange`)
 * y mensajes del Service Worker (`SW_SYNC_REQUESTED`) para drenar automáticamente `sync_outbox`
 * (T-2.13, ADR-0037 §5.3, RNF-19).
 */
export function useAutoOfflineSync(): void {
  const http = useHttpClient();

  useEffect(() => {
    const triggerFlush = () => {
      if (typeof navigator !== 'undefined' && !navigator.onLine) {
        return;
      }
      void flushSyncOutbox(http);
    };

    const handleVisibility = () => {
      if (typeof document !== 'undefined' && document.visibilityState === 'visible') {
        triggerFlush();
      }
    };

    const handleSwMessage = (event: MessageEvent<unknown>) => {
      if (
        typeof event.data === 'object' &&
        event.data !== null &&
        (event.data as { type?: string }).type === 'SW_SYNC_REQUESTED'
      ) {
        triggerFlush();
      }
    };

    window.addEventListener('online', triggerFlush);
    document.addEventListener('visibilitychange', handleVisibility);
    const swContainer = (globalThis.navigator as { serviceWorker?: EventTarget }).serviceWorker;
    swContainer?.addEventListener('message', handleSwMessage as EventListener);

    return () => {
      window.removeEventListener('online', triggerFlush);
      document.removeEventListener('visibilitychange', handleVisibility);
      swContainer?.removeEventListener('message', handleSwMessage as EventListener);
    };
  }, [http]);
}
