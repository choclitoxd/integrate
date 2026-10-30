import { useCallback, useEffect, useState, useSyncExternalStore } from 'react';

import {
  countPendingOutbox,
  countStoreRecords,
  getOfflineStorageDiagnostics,
  subscribeOfflineChanges,
} from './db';
import { readOfflineMeta } from './deviceMeta';
import { MAX_OFFLINE_BYTES, type OfflineDeviceMeta, type OfflineStorageDiagnostics } from './types';

function subscribeOnline(onChange: () => void): () => void {
  if (typeof window === 'undefined') {
    return () => undefined;
  }
  window.addEventListener('online', onChange);
  window.addEventListener('offline', onChange);
  return () => {
    window.removeEventListener('online', onChange);
    window.removeEventListener('offline', onChange);
  };
}

/**
 * Estado reactivo de conectividad del dispositivo (`FE-DIR-001` Directiva 6, ADR-0036 §5).
 */
export function useOnlineStatus(): boolean {
  return useSyncExternalStore(
    subscribeOnline,
    () => (typeof navigator !== 'undefined' ? navigator.onLine : true),
    () => true,
  );
}

export interface OfflineStatusSnapshot {
  readonly online: boolean;
  readonly loading: boolean;
  readonly diagnostics: OfflineStorageDiagnostics;
  readonly pendingOutboxCount: number;
  readonly rosterCount: number;
  readonly revocationCount: number;
  readonly deviceMeta: OfflineDeviceMeta;
  readonly refresh: () => Promise<void>;
}

const INITIAL_DIAGNOSTICS: OfflineStorageDiagnostics = {
  usageBytes: 0,
  quotaBytes: MAX_OFFLINE_BYTES,
  maxBudgetBytes: MAX_OFFLINE_BYTES,
  usageRatio: 0,
  nearLimit: false,
  exceeded: false,
  persisted: null,
  degradedReadOnly: false,
};

interface InternalSnapshot {
  readonly loading: boolean;
  readonly diagnostics: OfflineStorageDiagnostics;
  readonly pendingOutboxCount: number;
  readonly rosterCount: number;
  readonly revocationCount: number;
  readonly deviceMeta: OfflineDeviceMeta;
}

async function fetchOfflineSnapshot(): Promise<InternalSnapshot> {
  try {
    const [diag, pending, roster, revocations] = await Promise.all([
      getOfflineStorageDiagnostics(),
      countPendingOutbox(),
      countStoreRecords('operational_roster'),
      countStoreRecords('revocation_list'),
    ]);
    return {
      loading: false,
      diagnostics: diag,
      pendingOutboxCount: pending,
      rosterCount: roster,
      revocationCount: revocations,
      deviceMeta: readOfflineMeta(),
    };
  } catch {
    return {
      loading: false,
      diagnostics: { ...INITIAL_DIAGNOSTICS, degradedReadOnly: true },
      pendingOutboxCount: 0,
      rosterCount: 0,
      revocationCount: 0,
      deviceMeta: readOfflineMeta(),
    };
  }
}

/**
 * Hook de diagnóstico e infraestructura offline para el portal del Gestor de Enlace.
 */
export function useOfflineStatus(): OfflineStatusSnapshot {
  const online = useOnlineStatus();
  const [snapshot, setSnapshot] = useState<InternalSnapshot>(() => ({
    loading: true,
    diagnostics: INITIAL_DIAGNOSTICS,
    pendingOutboxCount: 0,
    rosterCount: 0,
    revocationCount: 0,
    deviceMeta: readOfflineMeta(),
  }));

  const refresh = useCallback(async () => {
    const next = await fetchOfflineSnapshot();
    setSnapshot(next);
  }, []);

  useEffect(() => {
    let active = true;
    void fetchOfflineSnapshot().then((next) => {
      if (active) setSnapshot(next);
    });
    const unsubscribe = subscribeOfflineChanges(() => {
      void fetchOfflineSnapshot().then((next) => {
        if (active) setSnapshot(next);
      });
    });
    return () => {
      active = false;
      unsubscribe();
    };
  }, []);

  return {
    online,
    loading: snapshot.loading,
    diagnostics: snapshot.diagnostics,
    pendingOutboxCount: snapshot.pendingOutboxCount,
    rosterCount: snapshot.rosterCount,
    revocationCount: snapshot.revocationCount,
    deviceMeta: snapshot.deviceMeta,
    refresh,
  };
}
