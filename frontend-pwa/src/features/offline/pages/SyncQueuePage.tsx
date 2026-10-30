import { useEffect, useState } from 'react';

import { useHttpClient } from '@/shared/api';
import { readOfflineMeta, subscribeOfflineChanges, useOfflineStatus } from '@/shared/offline';
import { Button, Icon } from '@/shared/ui';

import {
  flushSyncOutbox,
  listLocalValidationsWithOutbox,
  requeueDeadLetters,
  type FlushSyncResult,
  type LocalValidationQueueItem,
} from '../syncEngine';

function formatBogotaDateTime(iso: string): string {
  try {
    return new Intl.DateTimeFormat('es-CO', {
      timeZone: 'America/Bogota',
      dateStyle: 'short',
      timeStyle: 'medium',
    }).format(new Date(iso));
  } catch {
    return iso;
  }
}

/**
 * Vista de cola de asistencias diferidas y sincronización por lotes (`sync_outbox` + `local_validations`,
 * T-2.13, ADR-0037 §5, RNF-19).
 */
export function SyncQueuePage() {
  const http = useHttpClient();
  const { online, pendingOutboxCount } = useOfflineStatus();

  const [items, setItems] = useState<readonly LocalValidationQueueItem[]>([]);
  const [syncing, setSyncing] = useState(false);
  const [lastFlush, setLastFlush] = useState<FlushSyncResult | null>(null);
  const [lastSyncedAt, setLastSyncedAt] = useState<string | null>(
    () => readOfflineMeta().lastSyncedAt ?? null,
  );

  useEffect(() => {
    let active = true;
    const refresh = () => {
      void listLocalValidationsWithOutbox().then((loaded) => {
        if (!active) return;
        setItems(loaded);
        setLastSyncedAt(readOfflineMeta().lastSyncedAt ?? null);
      });
    };
    refresh();
    const unsubscribe = subscribeOfflineChanges(refresh);
    return () => {
      active = false;
      unsubscribe();
    };
  }, []);

  const handleFlushNow = async () => {
    if (syncing) return;
    setSyncing(true);
    try {
      const summary = await flushSyncOutbox(http, { force: true });
      setLastFlush(summary);
      setLastSyncedAt(readOfflineMeta().lastSyncedAt ?? null);
      const updatedItems = await listLocalValidationsWithOutbox();
      setItems(updatedItems);
    } finally {
      setSyncing(false);
    }
  };

  const deadLetterCount = items.filter(({ outbox }) => outbox?.status === 'DEAD_LETTER').length;

  const handleRetryDeadLetters = async () => {
    if (syncing) return;
    setSyncing(true);
    try {
      await requeueDeadLetters();
      const summary = await flushSyncOutbox(http, { force: true });
      setLastFlush(summary);
      setLastSyncedAt(readOfflineMeta().lastSyncedAt ?? null);
      setItems(await listLocalValidationsWithOutbox());
    } finally {
      setSyncing(false);
    }
  };

  return (
    <>
      <title>Cola de sincronización · Intégrate</title>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h1 className="text-3xl font-bold text-brand">Cola de sincronización</h1>
        <span
          role="status"
          className={`inline-flex min-h-9 items-center gap-1.5 rounded-full border-2 px-3 text-sm font-bold ${
            online
              ? 'border-success-border bg-success-bg text-success-text'
              : 'border-warning-border bg-warning-bg text-warning-text'
          }`}
        >
          <Icon name={online ? 'wifi' : 'wifiOff'} size={18} />
          {online ? 'En línea' : 'Sin conexión'}
        </span>
      </div>

      <p className="mt-2 text-ink-muted">
        Consulta las asistencias validadas en este teléfono y envíalas al servidor cuando recuperes
        señal.
      </p>

      <section
        aria-labelledby="resumen-cola-heading"
        className="mt-6 rounded-xl border-2 border-border-subtle bg-surface px-4 py-4"
      >
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 id="resumen-cola-heading" className="text-lg font-bold text-ink">
            Estado del lote diferido
          </h2>
          <span className="text-sm font-bold text-ink-muted">
            {pendingOutboxCount} {pendingOutboxCount === 1 ? 'pendiente' : 'pendientes'} por enviar
          </span>
        </div>

        {lastSyncedAt !== null && (
          <p className="mt-1 text-xs font-semibold text-ink-muted">
            Última sincronización confirmada: {formatBogotaDateTime(lastSyncedAt)}
          </p>
        )}

        {lastFlush !== null && (
          <div
            role="status"
            className="mt-3 rounded-lg border-2 border-brand bg-brand-soft px-3 py-2 text-sm font-semibold text-ink"
          >
            {lastFlush.dispatched === 0 ? (
              <span>No había asistencias pendientes en la cola para sincronizar.</span>
            ) : (
              <span>
                Lote procesado ({lastFlush.dispatched}{' '}
                {lastFlush.dispatched === 1 ? 'registro' : 'registros'}): {lastFlush.confirmed}{' '}
                confirmados, {lastFlush.conflicts} en conflicto, {lastFlush.rejected} rechazados,{' '}
                {lastFlush.retried} reprogramados.
              </span>
            )}
          </div>
        )}

        <div className="mt-4">
          <Button
            type="button"
            disabled={!online || syncing || pendingOutboxCount === 0}
            onClick={() => void handleFlushNow()}
          >
            {syncing ? 'Sincronizando…' : `Sincronizar ahora (${String(pendingOutboxCount)})`}
          </Button>
        </div>

        {deadLetterCount > 0 && (
          <div
            role="alert"
            className="mt-4 rounded-lg border-2 border-danger-border bg-danger-bg px-3 py-3 text-danger-text"
          >
            <p className="flex items-start gap-2 text-sm font-bold">
              <Icon name="alert" size={20} className="mt-0.5 shrink-0" />
              <span>
                {deadLetterCount}{' '}
                {deadLetterCount === 1
                  ? 'asistencia no pudo sincronizarse'
                  : 'asistencias no pudieron sincronizarse'}
                . No se han perdido: sigue guardada en este teléfono y puedes volver a enviarla.
              </span>
            </p>
            <div className="mt-3">
              <Button
                type="button"
                variant="secondary"
                disabled={!online || syncing}
                onClick={() => void handleRetryDeadLetters()}
              >
                {`Reintentar las ${String(deadLetterCount)} con error`}
              </Button>
            </div>
          </div>
        )}
      </section>

      <section aria-labelledby="historial-validaciones-heading" className="mt-6">
        <h2 id="historial-validaciones-heading" className="text-lg font-bold text-ink">
          Asistencias registradas en este teléfono ({items.length})
        </h2>

        {items.length === 0 ? (
          <p className="mt-3 rounded-xl border border-border-subtle bg-surface px-4 py-4 text-sm text-ink-muted">
            Aún no se han registrado asistencias en este dispositivo durante la jornada.
          </p>
        ) : (
          <ul className="mt-3 space-y-3">
            {items.map(({ validation, outbox, roster }) => {
              const isDeadLetter = outbox?.status === 'DEAD_LETTER';
              const isSynced = validation.syncStatus === 'SYNCED';
              const isConflict = validation.syncStatus === 'CONFLICT';
              const isRejected = validation.syncStatus === 'REJECTED' || isDeadLetter;

              const badgeStyle = isSynced
                ? 'border-success-border bg-success-bg text-success-text'
                : isConflict
                  ? 'border-warning-border bg-warning-bg text-warning-text'
                  : isRejected
                    ? 'border-danger-border bg-danger-bg text-danger-text'
                    : 'border-brand bg-brand-soft text-brand';

              const badgeLabel = isSynced
                ? 'Sincronizado'
                : isConflict
                  ? 'Conflicto conciliado'
                  : isDeadLetter
                    ? 'Error permanente'
                    : isRejected
                      ? 'Rechazado por servidor'
                      : outbox?.status === 'FAILED_RETRYABLE'
                        ? `Reintento (${String(outbox.retryCount)}/5)`
                        : 'Pendiente de envío';

              return (
                <li
                  key={validation.localValidationId}
                  className="rounded-xl border-2 border-border-subtle bg-surface px-4 py-3"
                >
                  <div className="flex flex-wrap items-start justify-between gap-2">
                    <div>
                      <p className="text-base font-bold text-ink">
                        {roster?.holderName ?? `Código ${validation.shortCode}`}
                      </p>
                      <p className="text-sm text-ink-muted">
                        Código: <span className="font-mono font-bold">{validation.shortCode}</span>{' '}
                        · Canal:{' '}
                        {validation.channel === 'OFFLINE_PWA'
                          ? 'Escáner QR'
                          : 'Contingencia manual'}
                      </p>
                      <p className="mt-0.5 text-xs text-ink-muted">
                        Registrado: {formatBogotaDateTime(validation.scannedAt)}
                      </p>
                    </div>
                    <span
                      className={`inline-flex min-h-7 items-center rounded-full border px-2.5 text-xs font-bold ${badgeStyle}`}
                    >
                      {badgeLabel}
                    </span>
                  </div>

                  {(validation.syncMessage ?? outbox?.lastError) && (
                    <p className="mt-2 rounded-lg border border-border-subtle bg-canvas px-3 py-1.5 text-xs font-semibold text-ink-muted">
                      {validation.syncMessage ?? outbox?.lastError}
                    </p>
                  )}
                </li>
              );
            })}
          </ul>
        )}
      </section>
    </>
  );
}
