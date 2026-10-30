import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { recordQuality } from '@/test/quality';

import {
  assertQuotaAvailable,
  closeOfflineDBForTesting,
  estimateOfflineUsageBytes,
  getOfflineDB,
  guardAndPurgeOnLogout,
  MAX_OFFLINE_BYTES,
  OBJECT_STORE_NAMES,
  OFFLINE_DB_NAME,
  OfflineQuotaExceededError,
  PendingSyncLogoutError,
} from './index';

describe('RNF-17 · Operación offline en IndexedDB (< 10 MB y resguardo de cola)', () => {
  beforeEach(async () => {
    await closeOfflineDBForTesting();
    await new Promise<void>((resolve) => {
      const req = indexedDB.deleteDatabase(OFFLINE_DB_NAME);
      req.onsuccess = () => {
        resolve();
      };
      req.onerror = () => {
        resolve();
      };
      req.onblocked = () => {
        resolve();
      };
    });
    localStorage.clear();
  });

  afterEach(async () => {
    await closeOfflineDBForTesting();
  });

  it('certifica los 6 Object Stores de IntegrateOfflineDB, cuota < 10 MB y bloqueo de cierre con cola pendiente', async ({
    task,
  }) => {
    const db = await getOfflineDB();
    const storesCount = db.objectStoreNames.length;

    // Insertar un lote representativo de padrón y validación local.
    await db.put('operational_roster', {
      voucherId: 'v-rnf17',
      jti: 'jti-rnf17',
      bookingId: 'b-rnf17',
      scheduleId: 's-rnf17',
      experienceTitle: 'Ruta Comunitaria Montes de María',
      shortCode: 'RNF17234',
      tokenSha256: '1'.repeat(64),
      signingKeyKid: 'kid-1',
      holderName: 'Lucía Perea',
      holderDocument: 'CC 52100200',
      slotsCount: 3,
      participants: [{ fullName: 'Lucía Perea', docType: 'CC', docNumber: '52100200' }],
      emergencyContact: { name: 'Jorge Perea', phone: '+573109998877' },
      rosterDate: '2026-10-30',
    });

    await db.put('sync_outbox', {
      mutationId: 'mut-rnf17',
      sequenceNumber: 1,
      syncBatchId: null,
      aggregateType: 'OFFLINE_VALIDATION',
      aggregateId: 'val-rnf17',
      actionType: 'CREATE_VALIDATION',
      payload: { voucherId: 'jti-rnf17' },
      status: 'QUEUED',
      retryCount: 0,
      maxRetries: 5,
      lastError: null,
      createdAt: '2026-10-30T12:00:00Z',
      updatedAt: '2026-10-30T12:00:00Z',
    });

    const { usageBytes } = await estimateOfflineUsageBytes();
    let quotaEnforced = false;
    try {
      await assertQuotaAvailable(MAX_OFFLINE_BYTES + 1024);
    } catch (error) {
      quotaEnforced = error instanceof OfflineQuotaExceededError;
    }

    let logoutGuarded = false;
    try {
      await guardAndPurgeOnLogout();
    } catch (error) {
      logoutGuarded = error instanceof PendingSyncLogoutError;
    }

    const usageMb = Number((usageBytes / (1024 * 1024)).toFixed(3));
    const pass =
      storesCount === OBJECT_STORE_NAMES.length &&
      usageBytes < MAX_OFFLINE_BYTES &&
      quotaEnforced &&
      logoutGuarded;

    recordQuality(task, {
      rnf: 'RNF-17',
      metric: 'Almacenamiento local IntegrateOfflineDB (6 stores) y tope de cuota',
      value: usageMb,
      unit: 'MB',
      threshold: '< 10 MB (6 Object Stores + Logout Guard activo)',
      pass,
      detail: `${String(storesCount)}/6 Object Stores creados; cuota dura de 10 MB verificada; Logout Guard protege sync_outbox.`,
    });

    expect(storesCount).toBe(6);
    expect(quotaEnforced).toBe(true);
    expect(logoutGuarded).toBe(true);
    expect(pass).toBe(true);
  });
});
