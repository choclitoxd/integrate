import { renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import {
  isNavigationFallbackAllowed,
  isPublicCatalogCacheablePath,
  isSensitiveApiPath,
} from '@/sw';

import {
  allocateSequenceNumber,
  assertQuotaAvailable,
  bogotaDateString,
  closeOfflineDBForTesting,
  countPendingOutbox,
  countStoreRecords,
  estimateOfflineUsageBytes,
  getOfflineDB,
  getOfflineStorageDiagnostics,
  getOrCreateDeviceId,
  guardAndPurgeOnLogout,
  INACTIVITY_PURGE_MS,
  isInactiveBeyondRetention,
  MAX_OFFLINE_BYTES,
  OBJECT_STORE_NAMES,
  OFFLINE_DB_NAME,
  OfflineQuotaExceededError,
  PendingSyncLogoutError,
  purgeExpiredRosterEntries,
  readOfflineMeta,
  registerServiceWorker,
  requestBackgroundSync,
  requestPersistentStorage,
  runInactivityMaintenance,
  touchOfflineActivity,
  updateOfflineMeta,
  useOfflineStatus,
  useOnlineStatus,
  type OfflineCatalogEntry,
  type OfflineRevocationEntry,
  type OfflineRosterEntry,
  type OfflineSigningKeyEntry,
  type OfflineValidationEntry,
  type SyncOutboxEntry,
} from './index';

async function resetIndexedDb() {
  await closeOfflineDBForTesting();
  await new Promise<void>((resolve, reject) => {
    const req = indexedDB.deleteDatabase(OFFLINE_DB_NAME);
    req.onsuccess = () => {
      resolve();
    };
    req.onerror = () => {
      reject(req.error ?? new Error('deleteDatabase failed'));
    };
    req.onblocked = () => {
      resolve();
    };
  });
}

function sampleRosterEntry(overrides: Partial<OfflineRosterEntry> = {}): OfflineRosterEntry {
  return {
    voucherId: 'v-001',
    jti: 'jti-001',
    bookingId: 'b-001',
    scheduleId: 's-001',
    experienceTitle: 'Sendero de Paz en Bojayá',
    shortCode: 'ABCD2345',
    tokenSha256: 'a'.repeat(64),
    signingKeyKid: 'key-2026-10',
    holderName: 'María Córdoba',
    holderDocument: 'CC 1020304050',
    slotsCount: 2,
    participants: [
      { fullName: 'María Córdoba', docType: 'CC', docNumber: '1020304050' },
      { fullName: 'Pedro Córdoba', docType: 'CC', docNumber: '1020304051' },
    ],
    emergencyContact: { name: 'Ana Córdoba', phone: '+573001112233' },
    rosterDate: '2026-10-30',
    localStatus: 'ISSUED',
    ...overrides,
  };
}

function sampleOutboxEntry(overrides: Partial<SyncOutboxEntry> = {}): SyncOutboxEntry {
  return {
    mutationId: 'mut-001',
    sequenceNumber: 1,
    syncBatchId: null,
    aggregateType: 'OFFLINE_VALIDATION',
    aggregateId: 'val-001',
    actionType: 'CREATE_VALIDATION',
    payload: { localValidationId: 'val-001', voucherId: 'jti-001', shortCode: 'ABCD2345' },
    status: 'QUEUED',
    retryCount: 0,
    maxRetries: 5,
    lastError: null,
    createdAt: '2026-10-30T10:00:00Z',
    updatedAt: '2026-10-30T10:00:00Z',
    ...overrides,
  };
}

describe('IntegrateOfflineDB e infraestructura offline (T-2.8 · ADR-0036)', () => {
  beforeEach(async () => {
    await resetIndexedDb();
    localStorage.clear();
  });

  afterEach(async () => {
    await resetIndexedDb();
  });

  it('crea exactamente los 6 Object Stores normativos y sus índices (incluyendo jti)', async () => {
    const db = await getOfflineDB();
    const storeNames = Array.from(db.objectStoreNames);
    expect(storeNames).toHaveLength(6);
    expect(storeNames.sort()).toEqual([...OBJECT_STORE_NAMES].sort());

    const tx = db.transaction(OBJECT_STORE_NAMES, 'readonly');
    expect(Array.from(tx.objectStore('read_only_catalog').indexNames)).toEqual(
      expect.arrayContaining([
        'idx_catalog_host',
        'idx_catalog_municipality',
        'idx_catalog_status',
      ]),
    );
    expect(Array.from(tx.objectStore('operational_roster').indexNames)).toEqual(
      expect.arrayContaining([
        'idx_roster_short_code',
        'idx_roster_schedule',
        'idx_roster_token_hash',
        'idx_roster_holder_doc',
        'idx_roster_jti',
      ]),
    );
    expect(Array.from(tx.objectStore('revocation_list').indexNames)).toEqual(
      expect.arrayContaining([
        'idx_revocation_short_code',
        'idx_revocation_timestamp',
        'idx_revocation_jti',
      ]),
    );
    expect(Array.from(tx.objectStore('public_signing_keys').indexNames)).toEqual(
      expect.arrayContaining(['idx_key_status']),
    );
    expect(Array.from(tx.objectStore('local_validations').indexNames)).toEqual(
      expect.arrayContaining([
        'idx_val_voucher_id',
        'idx_val_scanned_at',
        'idx_val_sync_status',
        'idx_val_short_code',
      ]),
    );
    expect(Array.from(tx.objectStore('sync_outbox').indexNames)).toEqual(
      expect.arrayContaining(['idx_outbox_order', 'idx_outbox_status', 'idx_outbox_aggregate']),
    );
    await tx.done;
  });

  it('permite consultar por índices en padrón (shortCode, jti) y lista de revocación', async () => {
    const db = await getOfflineDB();
    const roster = sampleRosterEntry();
    await db.put('operational_roster', roster);

    const byShortCode = await db.getFromIndex(
      'operational_roster',
      'idx_roster_short_code',
      'ABCD2345',
    );
    const byJti = await db.getFromIndex('operational_roster', 'idx_roster_jti', 'jti-001');
    expect(byShortCode?.holderName).toBe('María Córdoba');
    expect(byJti?.voucherId).toBe('v-001');

    const rev: OfflineRevocationEntry = {
      voucherId: 'v-002',
      jti: 'jti-002',
      shortCode: 'WXYZ6789',
      revokedAt: '2026-10-30T08:00:00Z',
      revocationReason: 'CANCELLED_BY_TOURIST',
    };
    await db.put('revocation_list', rev);
    const foundRev = await db.getFromIndex('revocation_list', 'idx_revocation_jti', 'jti-002');
    expect(foundRev?.shortCode).toBe('WXYZ6789');
  });

  it('impone el tope de cuota de 10 MB y reporta diagnósticos de StorageManager', async () => {
    const db = await getOfflineDB();
    await db.put('operational_roster', sampleRosterEntry());

    const { usageBytes, quotaBytes } = await estimateOfflineUsageBytes();
    expect(usageBytes).toBeGreaterThan(0);
    expect(quotaBytes).toBeLessThanOrEqual(MAX_OFFLINE_BYTES);

    await expect(assertQuotaAvailable(1024)).resolves.toBeUndefined();
    await expect(assertQuotaAvailable(MAX_OFFLINE_BYTES + 1)).rejects.toBeInstanceOf(
      OfflineQuotaExceededError,
    );

    const persisted = await requestPersistentStorage({
      persisted: () => Promise.resolve(false),
      persist: () => Promise.resolve(true),
    });
    expect(persisted).toBe(true);

    const volatileDiag = await getOfflineStorageDiagnostics({
      estimate: () => Promise.resolve({ usage: 9 * 1024 * 1024, quota: 50 * 1024 * 1024 }),
      persisted: () => Promise.resolve(false),
    });
    expect(volatileDiag.persisted).toBe(false);
    expect(volatileDiag.nearLimit).toBe(true);
    expect(volatileDiag.exceeded).toBe(false);
  });

  it('bloquea el cierre de sesión si hay mutaciones pendientes en sync_outbox y purga selectivamente cuando está vacío', async () => {
    const db = await getOfflineDB();

    const catalogEntry: OfflineCatalogEntry = {
      id: 'exp-1',
      hostId: 'h-1',
      hostName: 'Asociación Río Atrato',
      title: 'Recorrido Cacaotero',
      description: 'Cacao comunitario',
      safetyGuidelines: 'Botas',
      physicalIntensity: 'BAJA',
      durationMinutes: 120,
      municipalityCode: '27099',
      municipalityName: 'Bojayá',
      vereda: 'Bellavista',
      meetingPoint: 'Muelle principal',
      coordinates: { latitude: 6.5, longitude: -76.9 },
      maxCapacityPerSlot: 10,
      unitPriceCop: 80000,
      coverImageUrl: '',
      categories: ['AGROTURISMO'],
      schedules: [],
    };
    const keyEntry: OfflineSigningKeyEntry = {
      kid: 'key-2026-10',
      algorithm: 'RS256',
      publicKeyPem: '-----BEGIN PUBLIC KEY-----\nMIIB...\n-----END PUBLIC KEY-----',
      status: 'ACTIVE',
      validFrom: '2026-10-01T00:00:00Z',
    };
    const validationEntry: OfflineValidationEntry = {
      localValidationId: 'val-001',
      voucherId: 'jti-001',
      shortCode: 'ABCD2345',
      deviceId: getOrCreateDeviceId(),
      validatedBy: 'mgr-1',
      channel: 'OFFLINE_PWA',
      localResult: 'VALIDATED_OFFLINE',
      consumptionMarker: 'CONSUMIDO_LOCAL',
      signatureSha256: 'b'.repeat(64),
      scannedAt: '2026-10-30T09:30:00Z',
      syncStatus: 'PENDING',
    };

    await db.put('read_only_catalog', catalogEntry);
    await db.put('public_signing_keys', keyEntry);
    await db.put('operational_roster', sampleRosterEntry());
    await db.put('local_validations', validationEntry);
    await db.put('sync_outbox', sampleOutboxEntry({ status: 'QUEUED' }));

    expect(await countPendingOutbox()).toBe(1);
    await expect(guardAndPurgeOnLogout()).rejects.toBeInstanceOf(PendingSyncLogoutError);
    // El padrón no se borró porque el Logout Guard bloqueó la operación.
    expect(await countStoreRecords('operational_roster')).toBe(1);

    // Una vez vaciada la cola (sincronizada), el cierre de sesión limpia los stores sensibles y preserva catálogo/llaves.
    await db.clear('sync_outbox');
    await guardAndPurgeOnLogout();

    expect(await countStoreRecords('operational_roster')).toBe(0);
    expect(await countStoreRecords('revocation_list')).toBe(0);
    expect(await countStoreRecords('local_validations')).toBe(0);
    expect(await countStoreRecords('read_only_catalog')).toBe(1);
    expect(await countStoreRecords('public_signing_keys')).toBe(1);
  });

  it('purga el padrón vencido (> 24 h) y ejecuta mantenimiento por inactividad (> 7 días) preservando pendientes', async () => {
    const db = await getOfflineDB();
    await db.put(
      'operational_roster',
      sampleRosterEntry({ voucherId: 'v-ayer', rosterDate: '2026-10-28' }),
    );
    await db.put(
      'operational_roster',
      sampleRosterEntry({
        voucherId: 'v-hoy',
        jti: 'jti-002',
        shortCode: 'HJKL2345',
        tokenSha256: 'c'.repeat(64),
        rosterDate: '2026-10-30',
      }),
    );

    const removed = await purgeExpiredRosterEntries('2026-10-30');
    expect(removed).toBe(1);
    expect(await countStoreRecords('operational_roster')).toBe(1);

    // Simular 8 días de inactividad con 1 validación ya SYNCED y 1 aún PENDING.
    const eightDaysAgo = new Date(Date.now() - (INACTIVITY_PURGE_MS + 24 * 60 * 60 * 1000));
    updateOfflineMeta({ lastActivityAt: eightDaysAgo.toISOString() });
    expect(isInactiveBeyondRetention(readOfflineMeta())).toBe(true);

    await db.put('local_validations', {
      localValidationId: 'val-synced',
      voucherId: 'jti-old',
      shortCode: 'NPQR2345',
      deviceId: getOrCreateDeviceId(),
      validatedBy: 'mgr-1',
      channel: 'OFFLINE_PWA',
      localResult: 'VALIDATED_OFFLINE',
      consumptionMarker: 'CONSUMIDO_LOCAL',
      signatureSha256: 'd'.repeat(64),
      scannedAt: eightDaysAgo.toISOString(),
      syncStatus: 'SYNCED',
    });
    await db.put('local_validations', {
      localValidationId: 'val-pending',
      voucherId: 'jti-002',
      shortCode: 'HJKL2345',
      deviceId: getOrCreateDeviceId(),
      validatedBy: 'mgr-1',
      channel: 'OFFLINE_PWA',
      localResult: 'VALIDATED_OFFLINE',
      consumptionMarker: 'CONSUMIDO_LOCAL',
      signatureSha256: 'e'.repeat(64),
      scannedAt: eightDaysAgo.toISOString(),
      syncStatus: 'PENDING',
    });

    const maint = await runInactivityMaintenance();
    expect(maint.purgedByInactivity).toBe(true);
    expect(await countStoreRecords('operational_roster')).toBe(0);
    // La validación PENDING se preserva; la SYNCED se purga.
    expect(await countStoreRecords('local_validations')).toBe(1);
    expect((await db.get('local_validations', 'val-pending'))?.syncStatus).toBe('PENDING');
  });

  it('mantiene el deviceId estable y asigna sequenceNumber monotónico', () => {
    const id1 = getOrCreateDeviceId();
    const id2 = getOrCreateDeviceId();
    expect(id1).toBe(id2);
    expect(id1).toMatch(/^[0-9a-f-]{36}$/i);

    expect(allocateSequenceNumber()).toBe(1);
    expect(allocateSequenceNumber()).toBe(2);
    touchOfflineActivity();
    expect(bogotaDateString(new Date('2026-10-30T15:00:00Z'))).toBe('2026-10-30');
  });

  it('clasifica correctamente las rutas del Service Worker y coordina el registro', async () => {
    expect(isSensitiveApiPath('/api/v1/auth/refresh')).toBe(true);
    expect(isSensitiveApiPath('/api/v1/offline/roster')).toBe(true);
    expect(isSensitiveApiPath('/api/v1/bookings/lock')).toBe(true);
    expect(isSensitiveApiPath('/api/v1/catalog/categories')).toBe(false);

    expect(isPublicCatalogCacheablePath('/api/v1/catalog/categories')).toBe(true);
    expect(isPublicCatalogCacheablePath('/api/v1/experiences')).toBe(true);
    expect(isPublicCatalogCacheablePath('/api/v1/privacy-policies/current')).toBe(true);
    expect(isPublicCatalogCacheablePath('/api/v1/offline/sync')).toBe(false);

    expect(isNavigationFallbackAllowed('/gestor')).toBe(true);
    expect(isNavigationFallbackAllowed('/api/v1/experiences')).toBe(false);
    expect(isNavigationFallbackAllowed('/manual/index.html')).toBe(false);

    // En jsdom sin navigator.serviceWorker devuelve null/false de forma segura.
    expect(await registerServiceWorker()).toBeNull();
    expect(await requestBackgroundSync()).toBe(false);
  });

  it('expone el estado reactivo en useOfflineStatus y useOnlineStatus', async () => {
    const { result } = renderHook(() => ({
      online: useOnlineStatus(),
      status: useOfflineStatus(),
    }));
    await waitFor(() => {
      expect(result.current.status.loading).toBe(false);
    });
    expect(result.current.online).toBe(true);
    expect(result.current.status.diagnostics.maxBudgetBytes).toBe(MAX_OFFLINE_BYTES);

    const spy = vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false);
    window.dispatchEvent(new Event('offline'));
    await waitFor(() => {
      expect(result.current.online).toBe(false);
    });
    spy.mockRestore();
  });

  it('permite cerrar sesión cuando IndexedDB no se puede abrir (modo privado, almacenamiento bloqueado)', async () => {
    await closeOfflineDBForTesting();
    const open = vi.spyOn(indexedDB, 'open').mockImplementation(() => {
      throw new DOMException('Storage is blocked', 'SecurityError');
    });

    await expect(guardAndPurgeOnLogout()).resolves.toBeUndefined();
    expect(open).toHaveBeenCalled();
    await expect(runInactivityMaintenance()).rejects.toBeDefined();

    open.mockRestore();
    await closeOfflineDBForTesting();
  });
});
