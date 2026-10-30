import { useEffect, useState } from 'react';

import { useHttpClient } from '@/shared/api';
import {
  subscribeOfflineChanges,
  useOfflineStatus,
  type OfflineRosterEntry,
} from '@/shared/offline';
import { useSession } from '@/shared/session';
import { Button, Icon, TextField } from '@/shared/ui';

import { VerdictBanner } from '../components/VerdictBanner';
import {
  normalizeShortCode,
  redeemByManualShortCode,
  type RedemptionResult,
} from '../redemptionEngine';
import { listLocalRoster } from '../rosterService';

/**
 * Vista de contingencia manual por código alfanumérico corto (`shortCode`) y búsqueda en el
 * padrón local (T-2.12, ADR-0037 §4, RNF-20).
 */
export function ContingencyRedeemPage() {
  const http = useHttpClient();
  const session = useSession();
  const validatedBy = session.status === 'authenticated' ? session.user.id : 'link-manager';
  const { online } = useOfflineStatus();

  const [shortCode, setShortCode] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [rosterItems, setRosterItems] = useState<readonly OfflineRosterEntry[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<RedemptionResult | null>(null);

  useEffect(() => {
    let active = true;
    const loadRoster = () => {
      void listLocalRoster(searchQuery).then((items) => {
        if (active) setRosterItems(items);
      });
    };
    loadRoster();
    const unsubscribe = subscribeOfflineChanges(loadRoster);
    return () => {
      active = false;
      unsubscribe();
    };
  }, [searchQuery]);

  const handleRedeem = async (codeToRedeem: string) => {
    if (submitting) return;
    setSubmitting(true);
    try {
      const verdict = await redeemByManualShortCode({
        shortCode: codeToRedeem,
        validatedBy,
        http,
        online,
      });
      setResult(verdict);
      if (verdict.accepted) {
        setShortCode('');
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <title>Contingencia manual · Intégrate</title>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h1 className="text-3xl font-bold text-brand">Contingencia manual</h1>
        <span
          role="status"
          className={`inline-flex min-h-9 items-center gap-1.5 rounded-full border-2 px-3 text-sm font-bold ${
            online
              ? 'border-success-border bg-success-bg text-success-text'
              : 'border-warning-border bg-warning-bg text-warning-text'
          }`}
        >
          <Icon name={online ? 'wifi' : 'wifiOff'} size={18} />
          {online ? 'Canje en línea / local' : 'Canje local sin red'}
        </span>
      </div>

      <p className="mt-2 text-ink-muted">
        Si el código QR impreso o la pantalla del visitante están dañados, digita el código de 8
        caracteres del voucher o búscalo en el padrón descargado.
      </p>

      {result !== null && (
        <VerdictBanner
          result={result}
          onReset={() => {
            setResult(null);
          }}
        />
      )}

      <section
        aria-labelledby="canje-codigo-heading"
        className="mt-6 rounded-xl border-2 border-border-subtle bg-surface px-4 py-4"
      >
        <h2 id="canje-codigo-heading" className="text-lg font-bold text-ink">
          Canjear por código de 8 caracteres
        </h2>
        <form
          className="mt-3 space-y-4"
          onSubmit={(event) => {
            event.preventDefault();
            void handleRedeem(shortCode);
          }}
        >
          <TextField
            label="Código corto del voucher"
            name="shortCode"
            hint="8 caracteres alfanuméricos (sin O, I, L, 0 ni 1). Ejemplo: K7M9P2X4"
            value={shortCode}
            onChange={(event) => {
              setShortCode(normalizeShortCode(event.target.value).slice(0, 8));
            }}
            placeholder="K7M9P2X4"
            autoComplete="off"
          />
          <Button type="submit" disabled={submitting || shortCode.trim().length === 0}>
            {submitting ? 'Validando código…' : 'Validar código y registrar asistencia'}
          </Button>
        </form>
      </section>

      <section
        aria-labelledby="padron-busqueda-heading"
        className="mt-6 rounded-xl border-2 border-border-subtle bg-surface px-4 py-4"
      >
        <div className="flex items-center justify-between gap-2">
          <h2 id="padron-busqueda-heading" className="text-lg font-bold text-ink">
            Padrón descargado en este teléfono ({rosterItems.length})
          </h2>
        </div>

        <div className="mt-3">
          <TextField
            label="Buscar por titular, documento o código"
            name="searchRoster"
            value={searchQuery}
            onChange={(event) => {
              setSearchQuery(event.target.value);
            }}
            placeholder="Ej. María Gómez o 1094..."
            autoComplete="off"
          />
        </div>

        {rosterItems.length === 0 ? (
          <p className="mt-4 rounded-lg border border-border-subtle bg-canvas px-4 py-3 text-sm text-ink-muted">
            No hay vouchers en el padrón local que coincidan con la búsqueda.
          </p>
        ) : (
          <ul className="mt-4 space-y-3">
            {rosterItems.map((entry) => {
              const consumed = entry.localStatus === 'CONSUMIDO_LOCAL';
              return (
                <li
                  key={entry.voucherId}
                  className="rounded-xl border-2 border-border-subtle bg-canvas px-4 py-3"
                >
                  <div className="flex flex-wrap items-start justify-between gap-2">
                    <div>
                      <p className="text-base font-bold text-ink">{entry.holderName}</p>
                      <p className="text-sm text-ink-muted">
                        Doc: {entry.holderDocument} · Cupos: {entry.slotsCount} · Código:{' '}
                        <span className="font-mono font-bold text-ink">{entry.shortCode}</span>
                      </p>
                      <p className="mt-0.5 text-xs text-ink-muted">{entry.experienceTitle}</p>
                    </div>
                    <span
                      className={`inline-flex min-h-7 items-center rounded-full border px-2.5 text-xs font-bold ${
                        consumed
                          ? 'border-warning-border bg-warning-bg text-warning-text'
                          : 'border-success-border bg-success-bg text-success-text'
                      }`}
                    >
                      {consumed ? 'Consumido en este teléfono' : 'Disponible'}
                    </span>
                  </div>

                  {!consumed && (
                    <div className="mt-3">
                      <Button
                        type="button"
                        variant="secondary"
                        disabled={submitting}
                        onClick={() => void handleRedeem(entry.shortCode)}
                      >
                        Canjear asistencia ({entry.shortCode})
                      </Button>
                    </div>
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
