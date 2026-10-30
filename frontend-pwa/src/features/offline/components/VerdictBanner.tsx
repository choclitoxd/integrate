import { Button, Icon } from '@/shared/ui';

import type { RedemptionResult } from '../redemptionEngine';

function formatBogotaTime(iso: string): string {
  try {
    return new Intl.DateTimeFormat('es-CO', {
      timeZone: 'America/Bogota',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    }).format(new Date(iso));
  } catch {
    return iso;
  }
}

export interface VerdictBannerProps {
  readonly result: RedemptionResult;
  readonly onReset: () => void;
}

/**
 * Tarjeta multimodal de veredicto de validación territorial (T-2.10, T-2.11, T-2.12, ADR-0037 §2.5, RNF-14).
 * Combina semántica cromática AAA (`success` / `danger`), iconografía redundante y texto explícito.
 */
export function VerdictBanner({ result, onReset }: VerdictBannerProps) {
  const { accepted, title, message, voucher, previousConsumedAt, channel, mode } = result;

  return (
    <section
      role="alert"
      aria-live="assertive"
      className={`mt-4 rounded-xl border-2 px-4 py-4 ${
        accepted
          ? 'border-success-border bg-success-bg text-success-text'
          : 'border-danger-border bg-danger-bg text-danger-text'
      }`}
    >
      <div className="flex items-start gap-3">
        <span className="mt-0.5 shrink-0">
          <Icon name={accepted ? 'check' : 'alert'} size={28} />
        </span>
        <div className="min-w-0 flex-1">
          <p className="text-xs font-bold tracking-wider uppercase">
            {accepted ? 'Veredicto: Válido · Consumo local registrado' : 'Veredicto: Rechazado'}
          </p>
          <h2 className="mt-0.5 text-xl font-bold">{title}</h2>
          <p className="mt-1 text-base font-semibold">{message}</p>

          {previousConsumedAt !== undefined && (
            <p className="mt-2 rounded-lg border border-danger-border bg-canvas px-3 py-2 text-sm font-bold text-danger-text">
              Hora del consumo previo en este teléfono: {formatBogotaTime(previousConsumedAt)}
            </p>
          )}
        </div>
      </div>

      {voucher !== undefined && (
        <div className="mt-4 rounded-lg border border-border-subtle bg-canvas px-4 py-3 text-ink">
          <dl className="grid grid-cols-2 gap-2 text-sm">
            <div>
              <dt className="text-ink-muted">Titular</dt>
              <dd className="font-bold">{voucher.holderName}</dd>
            </div>
            <div>
              <dt className="text-ink-muted">Documento</dt>
              <dd className="font-bold">{voucher.holderDocument}</dd>
            </div>
            <div>
              <dt className="text-ink-muted">Cupos autorizados</dt>
              <dd className="font-bold">
                {voucher.slotsCount} {voucher.slotsCount === 1 ? 'persona' : 'personas'}
              </dd>
            </div>
            <div>
              <dt className="text-ink-muted">Código de reserva</dt>
              <dd className="font-mono font-bold">{voucher.shortCode}</dd>
            </div>
            <div className="col-span-2">
              <dt className="text-ink-muted">Experiencia</dt>
              <dd className="font-bold">{voucher.experienceTitle}</dd>
            </div>
            <div className="col-span-2">
              <dt className="text-ink-muted">Contacto de emergencia</dt>
              <dd className="font-semibold">
                {voucher.emergencyContact.name} · {voucher.emergencyContact.phone}
              </dd>
            </div>
          </dl>

          {voucher.participants.length > 0 && (
            <div className="mt-3 border-t border-border-subtle pt-2">
              <p className="text-xs font-bold tracking-wider text-ink-muted uppercase">
                Participantes inscritos ({voucher.participants.length})
              </p>
              <ul className="mt-1 space-y-1 text-sm">
                {voucher.participants.map((participant, idx) => (
                  <li
                    key={`${participant.docNumber}-${String(idx)}`}
                    className="flex justify-between"
                  >
                    <span className="font-semibold text-ink">{participant.fullName}</span>
                    <span className="text-ink-muted">
                      {participant.docType} {participant.docNumber}
                    </span>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}

      <div className="mt-4 flex flex-wrap items-center justify-between gap-2 text-xs font-semibold">
        <span>
          Canal: {channel === 'OFFLINE_PWA' ? 'Escáner QR' : 'Contingencia manual'} · Modo:{' '}
          {mode === 'OFFLINE' ? 'Local (IndexedDB)' : 'En línea'}
        </span>
      </div>

      <div className="mt-3">
        <Button type="button" variant="secondary" onClick={onReset}>
          Validar otro visitante
        </Button>
      </div>
    </section>
  );
}
