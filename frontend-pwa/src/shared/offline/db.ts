import { openDB, type DBSchema, type IDBPDatabase, type IDBPTransaction } from 'idb';

import {
  bogotaDateString,
  isInactiveBeyondRetention,
  readOfflineMeta,
  updateOfflineMeta,
} from './deviceMeta';
import {
  MAX_OFFLINE_BYTES,
  OBJECT_STORE_NAMES,
  OFFLINE_DB_NAME,
  OFFLINE_DB_VERSION,
  WARN_OFFLINE_BYTES,
  type ObjectStoreName,
  type OfflineCatalogEntry,
  type OfflineRevocationEntry,
  type OfflineRosterEntry,
  type OfflineSigningKeyEntry,
  type OfflineStorageDiagnostics,
  type OfflineValidationChannel,
  type OfflineValidationEntry,
  type OfflineValidationSyncStatus,
  type OutboxAggregateType,
  type OutboxStatus,
  type SyncOutboxEntry,
} from './types';

/**
 * Esquema tipado de `IntegrateOfflineDB` (v1) con los 6 Object Stores normativos.
 * Trazabilidad: `docs/architecture/offline_data_indexeddb.md` §2.2–§2.3 y ADR-0036 §1.
 */
export interface IntegrateOfflineDBSchema extends DBSchema {
  read_only_catalog: {
    key: string;
    value: OfflineCatalogEntry;
    indexes: {
      idx_catalog_host: string;
      idx_catalog_municipality: string;
      idx_catalog_status: string;
    };
  };
  operational_roster: {
    key: string;
    value: OfflineRosterEntry;
    indexes: {
      idx_roster_short_code: string;
      idx_roster_schedule: string;
      idx_roster_token_hash: string;
      idx_roster_holder_doc: string;
      idx_roster_jti: string;
    };
  };
  revocation_list: {
    key: string;
    value: OfflineRevocationEntry;
    indexes: {
      idx_revocation_short_code: string;
      idx_revocation_timestamp: string;
      idx_revocation_jti: string;
    };
  };
  public_signing_keys: {
    key: string;
    value: OfflineSigningKeyEntry;
    indexes: {
      idx_key_status: 'ACTIVE' | 'RETIRED';
    };
  };
  local_validations: {
    key: string;
    value: OfflineValidationEntry;
    indexes: {
      idx_val_voucher_id: string;
      idx_val_scanned_at: string;
      idx_val_sync_status: OfflineValidationSyncStatus;
      idx_val_short_code: string;
    };
  };
  sync_outbox: {
    key: string;
    value: SyncOutboxEntry;
    indexes: {
      idx_outbox_order: number;
      idx_outbox_status: OutboxStatus;
      idx_outbox_aggregate: [OutboxAggregateType, string];
    };
  };
}

export type IntegrateOfflineDB = IDBPDatabase<IntegrateOfflineDBSchema>;

/** Error lanzado cuando una operación offline superaría el límite de 10 MB (T-2.8 / RNF-17). */
export class OfflineQuotaExceededError extends Error {
  readonly currentBytes: number;
  readonly additionalBytes: number;
  readonly maxBytes: number;

  constructor(currentBytes: number, additionalBytes: number, maxBytes: number = MAX_OFFLINE_BYTES) {
    super(
      `El almacenamiento offline supera el límite permitido de ${(maxBytes / (1024 * 1024)).toFixed(0)} MB.`,
    );
    this.name = 'OfflineQuotaExceededError';
    this.currentBytes = currentBytes;
    this.additionalBytes = additionalBytes;
    this.maxBytes = maxBytes;
  }
}

/** Error lanzado cuando se intenta cerrar sesión con asistencias pendientes en `sync_outbox`. */
export class PendingSyncLogoutError extends Error {
  readonly pendingCount: number;

  constructor(pendingCount: number) {
    super(
      `No puedes cerrar sesión todavía: hay ${String(pendingCount)} ${
        pendingCount === 1 ? 'asistencia pendiente' : 'asistencias pendientes'
      } de sincronizar con el servidor.`,
    );
    this.name = 'PendingSyncLogoutError';
    this.pendingCount = pendingCount;
  }
}

let dbPromise: Promise<IntegrateOfflineDB> | null = null;
let degradedReadOnly = false;
const changeListeners = new Set<() => void>();

export function subscribeOfflineChanges(listener: () => void): () => void {
  changeListeners.add(listener);
  return () => {
    changeListeners.delete(listener);
  };
}

export function notifyOfflineChanges(): void {
  for (const listener of changeListeners) {
    listener();
  }
}

/**
 * Aplica de forma idempotente y no destructiva el esquema de los 6 Object Stores (`onupgradeneeded`).
 * Prohibido invocar `deleteDatabase()` o `clear()` sobre almacenes de auditoría (`offline_data_indexeddb.md` §4.3.1).
 */
export function upgradeOfflineDatabase(
  db: IntegrateOfflineDB,
  transaction: IDBPTransaction<
    IntegrateOfflineDBSchema,
    typeof OBJECT_STORE_NAMES,
    'versionchange'
  >,
): void {
  // 1. read_only_catalog
  const catalogStore = db.objectStoreNames.contains('read_only_catalog')
    ? transaction.objectStore('read_only_catalog')
    : db.createObjectStore('read_only_catalog', { keyPath: 'id' });
  if (!catalogStore.indexNames.contains('idx_catalog_host')) {
    catalogStore.createIndex('idx_catalog_host', 'hostId', { unique: false });
  }
  if (!catalogStore.indexNames.contains('idx_catalog_municipality')) {
    catalogStore.createIndex('idx_catalog_municipality', 'municipalityCode', { unique: false });
  }
  if (!catalogStore.indexNames.contains('idx_catalog_status')) {
    catalogStore.createIndex('idx_catalog_status', 'status', { unique: false });
  }

  // 2. operational_roster
  const rosterStore = db.objectStoreNames.contains('operational_roster')
    ? transaction.objectStore('operational_roster')
    : db.createObjectStore('operational_roster', { keyPath: 'voucherId' });
  if (!rosterStore.indexNames.contains('idx_roster_short_code')) {
    rosterStore.createIndex('idx_roster_short_code', 'shortCode', { unique: true });
  }
  if (!rosterStore.indexNames.contains('idx_roster_schedule')) {
    rosterStore.createIndex('idx_roster_schedule', 'scheduleId', { unique: false });
  }
  if (!rosterStore.indexNames.contains('idx_roster_token_hash')) {
    rosterStore.createIndex('idx_roster_token_hash', 'tokenSha256', { unique: true });
  }
  if (!rosterStore.indexNames.contains('idx_roster_holder_doc')) {
    rosterStore.createIndex('idx_roster_holder_doc', 'holderDocument', { unique: false });
  }
  if (!rosterStore.indexNames.contains('idx_roster_jti')) {
    rosterStore.createIndex('idx_roster_jti', 'jti', { unique: true });
  }

  // 3. revocation_list
  const revocationStore = db.objectStoreNames.contains('revocation_list')
    ? transaction.objectStore('revocation_list')
    : db.createObjectStore('revocation_list', { keyPath: 'voucherId' });
  if (!revocationStore.indexNames.contains('idx_revocation_short_code')) {
    revocationStore.createIndex('idx_revocation_short_code', 'shortCode', { unique: true });
  }
  if (!revocationStore.indexNames.contains('idx_revocation_timestamp')) {
    revocationStore.createIndex('idx_revocation_timestamp', 'revokedAt', { unique: false });
  }
  if (!revocationStore.indexNames.contains('idx_revocation_jti')) {
    revocationStore.createIndex('idx_revocation_jti', 'jti', { unique: true });
  }

  // 4. public_signing_keys
  const keysStore = db.objectStoreNames.contains('public_signing_keys')
    ? transaction.objectStore('public_signing_keys')
    : db.createObjectStore('public_signing_keys', { keyPath: 'kid' });
  if (!keysStore.indexNames.contains('idx_key_status')) {
    keysStore.createIndex('idx_key_status', 'status', { unique: false });
  }

  // 5. local_validations
  const validationsStore = db.objectStoreNames.contains('local_validations')
    ? transaction.objectStore('local_validations')
    : db.createObjectStore('local_validations', { keyPath: 'localValidationId' });
  if (!validationsStore.indexNames.contains('idx_val_voucher_id')) {
    validationsStore.createIndex('idx_val_voucher_id', 'voucherId', { unique: false });
  }
  if (!validationsStore.indexNames.contains('idx_val_scanned_at')) {
    validationsStore.createIndex('idx_val_scanned_at', 'scannedAt', { unique: false });
  }
  if (!validationsStore.indexNames.contains('idx_val_sync_status')) {
    validationsStore.createIndex('idx_val_sync_status', 'syncStatus', { unique: false });
  }
  if (!validationsStore.indexNames.contains('idx_val_short_code')) {
    validationsStore.createIndex('idx_val_short_code', 'shortCode', { unique: false });
  }

  // 6. sync_outbox
  const outboxStore = db.objectStoreNames.contains('sync_outbox')
    ? transaction.objectStore('sync_outbox')
    : db.createObjectStore('sync_outbox', { keyPath: 'mutationId' });
  if (!outboxStore.indexNames.contains('idx_outbox_order')) {
    outboxStore.createIndex('idx_outbox_order', 'sequenceNumber', { unique: true });
  }
  if (!outboxStore.indexNames.contains('idx_outbox_status')) {
    outboxStore.createIndex('idx_outbox_status', 'status', { unique: false });
  }
  if (!outboxStore.indexNames.contains('idx_outbox_aggregate')) {
    outboxStore.createIndex('idx_outbox_aggregate', ['aggregateType', 'aggregateId'], {
      unique: false,
    });
  }
}

/** Abre o reutiliza la instancia singleton de `IntegrateOfflineDB`. */
export function getOfflineDB(): Promise<IntegrateOfflineDB> {
  dbPromise ??= openDB<IntegrateOfflineDBSchema>(OFFLINE_DB_NAME, OFFLINE_DB_VERSION, {
    upgrade(db, _oldVersion, _newVersion, transaction) {
      upgradeOfflineDatabase(db, transaction);
    },
    terminated() {
      dbPromise = null;
    },
  }).catch((error: unknown) => {
    dbPromise = null;
    degradedReadOnly = true;
    notifyOfflineChanges();
    throw error;
  });
  return dbPromise;
}

/** Cierra la conexión en memoria (útil en pruebas para reiniciar el estado entre casos). */
export async function closeOfflineDBForTesting(): Promise<void> {
  if (dbPromise) {
    try {
      const db = await dbPromise;
      db.close();
    } catch {
      // Ignorado si la apertura había fallado.
    }
    dbPromise = null;
  }
  degradedReadOnly = false;
}

/** Limpia los 6 Object Stores (exclusivo para aislamiento entre casos de prueba). */
export async function clearAllOfflineStores(): Promise<void> {
  const db = await getOfflineDB();
  const tx = db.transaction(OBJECT_STORE_NAMES, 'readwrite');
  for (const storeName of OBJECT_STORE_NAMES) {
    await tx.objectStore(storeName).clear();
  }
  await tx.done;
}

/** Calcula el tamaño aproximado en bytes UTF-8 de un valor serializable (con 30 % de overhead de índices B-Tree). */
export function estimateSerializedBytes(value: unknown, includeIndexOverhead = true): number {
  const json = typeof value === 'undefined' ? '' : JSON.stringify(value);
  const rawBytes = new TextEncoder().encode(json).byteLength;
  return includeIndexOverhead ? Math.ceil(rawBytes * 1.3) : rawBytes;
}

/** Calcula el uso real o estimado en bytes de `IntegrateOfflineDB`. */
export async function estimateOfflineUsageBytes(
  customStorageManager?: Pick<StorageManager, 'estimate'>,
): Promise<{ readonly usageBytes: number; readonly quotaBytes: number }> {
  const db = await getOfflineDB();
  let computedBytes = 0;
  for (const storeName of OBJECT_STORE_NAMES) {
    const allRecords = await db.getAll(storeName);
    computedBytes += estimateSerializedBytes(allRecords, true);
  }

  const manager =
    customStorageManager ??
    (typeof navigator !== 'undefined' && 'storage' in navigator ? navigator.storage : undefined);

  if (manager && typeof manager.estimate === 'function') {
    try {
      const est = await manager.estimate();
      const browserUsage = typeof est.usage === 'number' ? est.usage : 0;
      const browserQuota =
        typeof est.quota === 'number' && est.quota > 0 ? est.quota : MAX_OFFLINE_BYTES;
      return {
        usageBytes: Math.max(computedBytes, browserUsage),
        quotaBytes: Math.min(browserQuota, MAX_OFFLINE_BYTES),
      };
    } catch {
      // Fallback al cálculo determinista de registros.
    }
  }

  return { usageBytes: computedBytes, quotaBytes: MAX_OFFLINE_BYTES };
}

/**
 * Garantiza que agregar `additionalBytes` no supere el tope de 10 MB (`MAX_OFFLINE_BYTES`).
 * Lanza `OfflineQuotaExceededError` antes de iniciar la escritura si excede el presupuesto.
 */
export async function assertQuotaAvailable(
  additionalBytes: number,
  maxBytes: number = MAX_OFFLINE_BYTES,
): Promise<void> {
  const { usageBytes } = await estimateOfflineUsageBytes();
  if (usageBytes + additionalBytes > maxBytes) {
    throw new OfflineQuotaExceededError(usageBytes, additionalBytes, maxBytes);
  }
}

/**
 * Solicita almacenamiento persistente (`StorageManager.persist()`) al iniciar sesión del Gestor o Anfitrión
 * (`offline_data_indexeddb.md` §4.1.1).
 */
export async function requestPersistentStorage(
  customStorageManager?: Pick<StorageManager, 'persist' | 'persisted'>,
): Promise<boolean | null> {
  const manager =
    customStorageManager ??
    (typeof navigator !== 'undefined' && 'storage' in navigator ? navigator.storage : undefined);
  if (!manager) return null;

  try {
    if (typeof manager.persisted === 'function') {
      const already = await manager.persisted();
      if (already) return true;
    }
    if (typeof manager.persist === 'function') {
      return await manager.persist();
    }
  } catch {
    return null;
  }
  return null;
}

/** Obtiene el diagnóstico completo de cuota y persistencia para la interfaz del Gestor. */
export async function getOfflineStorageDiagnostics(
  customStorageManager?: Pick<StorageManager, 'estimate' | 'persisted'>,
): Promise<OfflineStorageDiagnostics> {
  const { usageBytes, quotaBytes } = await estimateOfflineUsageBytes(customStorageManager);
  const manager =
    customStorageManager ??
    (typeof navigator !== 'undefined' && 'storage' in navigator ? navigator.storage : undefined);
  let persisted: boolean | null = null;
  if (manager && typeof manager.persisted === 'function') {
    try {
      persisted = await manager.persisted();
    } catch {
      persisted = null;
    }
  }
  const usageRatio = Math.min(1, usageBytes / MAX_OFFLINE_BYTES);
  return {
    usageBytes,
    quotaBytes,
    maxBudgetBytes: MAX_OFFLINE_BYTES,
    usageRatio,
    nearLimit: usageBytes >= WARN_OFFLINE_BYTES,
    exceeded: usageBytes > MAX_OFFLINE_BYTES,
    persisted,
    degradedReadOnly,
  };
}

const PENDING_OUTBOX_STATUSES: readonly OutboxStatus[] = [
  'QUEUED',
  'PROCESSING',
  'FAILED_RETRYABLE',
];

/** Cuenta cuántas mutaciones están pendientes de sincronizar en `sync_outbox` (*Logout Guard*). */
export async function countPendingOutbox(): Promise<number> {
  const db = await getOfflineDB();
  const tx = db.transaction('sync_outbox', 'readonly');
  const idx = tx.store.index('idx_outbox_status');
  let total = 0;
  for (const status of PENDING_OUTBOX_STATUSES) {
    total += await idx.count(status);
  }
  await tx.done;
  return total;
}

/** Cuenta cuántos registros existen en un Object Store específico. */
export async function countStoreRecords(storeName: ObjectStoreName): Promise<number> {
  const db = await getOfflineDB();
  return db.count(storeName);
}

/**
 * Purga los registros del padrón (`operational_roster`) cuya fecha operativa sea anterior a `todayBogota`
 * (`offline_data_indexeddb.md` §4.2.1: TTL de 24 h).
 */
export async function purgeExpiredRosterEntries(
  todayBogota: string = bogotaDateString(),
): Promise<number> {
  const db = await getOfflineDB();
  const tx = db.transaction('operational_roster', 'readwrite');
  const all = await tx.store.getAll();
  let removed = 0;
  for (const entry of all) {
    if (entry.rosterDate && entry.rosterDate < todayBogota) {
      await tx.store.delete(entry.voucherId);
      removed += 1;
    }
  }
  await tx.done;

  const meta = readOfflineMeta();
  if (meta.rosterMeta && meta.rosterMeta.rosterDate < todayBogota) {
    updateOfflineMeta({ rosterMeta: null });
  }
  if (removed > 0) notifyOfflineChanges();
  return removed;
}

/**
 * Ejecuta la rutina de limpieza por inactividad superior a 7 días (`offline_data_indexeddb.md` §4.2.2 y §4.3.1).
 * Preserva las mutaciones aún pendientes en `sync_outbox` y su evidencia en `local_validations`.
 */
export async function runInactivityMaintenance(
  options: { readonly storage?: Storage; readonly now?: () => Date } = {},
): Promise<{ readonly purgedByInactivity: boolean; readonly expiredRosterRemoved: number }> {
  const now = options.now?.() ?? new Date();
  const meta = readOfflineMeta(options);

  if (isInactiveBeyondRetention(meta, now)) {
    const db = await getOfflineDB();
    const tx = db.transaction(
      ['read_only_catalog', 'operational_roster', 'revocation_list', 'local_validations'],
      'readwrite',
    );
    await tx.objectStore('read_only_catalog').clear();
    await tx.objectStore('operational_roster').clear();
    await tx.objectStore('revocation_list').clear();

    // Solo borra validaciones locales ya sincronizadas, preservando las pendientes (regla §4.3.1).
    const validationsStore = tx.objectStore('local_validations');
    const allValidations = await validationsStore.getAll();
    for (const val of allValidations) {
      if (val.syncStatus === 'SYNCED') {
        await validationsStore.delete(val.localValidationId);
      }
    }
    await tx.done;
    updateOfflineMeta(
      {
        rosterMeta: null,
        catalogUpdatedAt: null,
        lastActivityAt: now.toISOString(),
      },
      options,
    );
    notifyOfflineChanges();
    return { purgedByInactivity: true, expiredRosterRemoved: 0 };
  }

  const expiredRosterRemoved = await purgeExpiredRosterEntries(bogotaDateString(now));
  return { purgedByInactivity: false, expiredRosterRemoved };
}

/**
 * Cierre de sesión seguro (*Logout Guard*, `offline_data_indexeddb.md` §4.2.2):
 * - Si hay registros pendientes en `sync_outbox` (`QUEUED`, `PROCESSING`, `FAILED_RETRYABLE`), lanza `PendingSyncLogoutError`.
 * - Si está en 0, purga obligatoriamente `operational_roster`, `revocation_list` y `local_validations`,
 *   preservando `read_only_catalog` y `public_signing_keys`.
 */
export async function guardAndPurgeOnLogout(
  options: { readonly storage?: Storage; readonly now?: () => Date } = {},
): Promise<void> {
  // Si IndexedDB no abre (modo privado de Safari, almacenamiento bloqueado), no hay cola que proteger ni datos locales
  // que purgar: se permite cerrar sesión. Bloquearla dejaría al usuario atrapado, incluido un turista que nunca usó el
  // modo offline. Un error que no sea de apertura (p. ej. `PendingSyncLogoutError`) sí se propaga.
  let db: IntegrateOfflineDB;
  try {
    db = await getOfflineDB();
  } catch {
    return;
  }

  const pending = await countPendingOutbox();
  if (pending > 0) {
    throw new PendingSyncLogoutError(pending);
  }

  const tx = db.transaction(
    ['operational_roster', 'revocation_list', 'local_validations'],
    'readwrite',
  );
  await tx.objectStore('operational_roster').clear();
  await tx.objectStore('revocation_list').clear();
  await tx.objectStore('local_validations').clear();
  await tx.done;

  updateOfflineMeta({ rosterMeta: null }, options);
  notifyOfflineChanges();
}

export type { OfflineValidationChannel };
