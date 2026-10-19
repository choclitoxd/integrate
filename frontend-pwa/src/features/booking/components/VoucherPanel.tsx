import { useState } from 'react';

import { ApiError } from '@/shared/api';
import { formatLongDate } from '@/shared/lib';
import { Button, Icon, ProblemNotice, Skeleton } from '@/shared/ui';

import type { Voucher } from '../api';
import { groupShortCode } from '../qr/matrix';
import { QrCode } from '../qr/QrCode';
import { QrFullscreen } from '../qr/QrFullscreen';
import { isVoucherNotReady, useVoucher } from '../queries';
import { externalNavigation } from '../wompi';

const STATUS_NOTE: Readonly<Record<Exclude<Voucher['status'], 'ISSUED'>, string>> = {
  CONSUMED: 'Este voucher ya se usó.',
  REVOKED: 'Este voucher fue anulado.',
  EXPIRED: 'Este voucher venció.',
};

function validLabel(voucher: Voucher): string {
  return `válido el ${formatLongDate(voucher.validDate)}`;
}

/** La entrada a pantalla completa debe pedirse dentro del gesto del usuario. */
function enterFullscreen() {
  // iOS Safari no implementa la API en elementos que no son <video>.
  const root = document.documentElement as Partial<Pick<HTMLElement, 'requestFullscreen'>>;
  if (root.requestFullscreen) void root.requestFullscreen().catch(() => undefined);
}

/**
 * Voucher de una reserva confirmada (T-1.10e, ADR-0017). Con `used` (experiencia realizada, ADR-0023 §2) se muestra
 * como comprobante: código y PDF, sin QR, porque ya no sirve para ingresar.
 */
export function VoucherPanel({ bookingId, used = false }: { bookingId: string; used?: boolean }) {
  const voucher = useVoucher(bookingId, true);
  const [fullscreen, setFullscreen] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const [downloadError, setDownloadError] = useState<unknown>(null);

  // El PDF caduca a los 5 min: se pide una URL fresca en el momento del clic (ADR-0017 §5).
  async function downloadPdf() {
    setDownloading(true);
    setDownloadError(null);
    try {
      const fresh = await voucher.refetch({ throwOnError: true });
      const url = fresh.data?.pdfUrl;
      if (url) externalNavigation.assign(url);
    } catch (error) {
      setDownloadError(error);
    } finally {
      setDownloading(false);
    }
  }

  if (voucher.isPending || (voucher.isError && isVoucherNotReady(voucher.error))) {
    const stillTrying = voucher.isPending || voucher.isFetching || voucher.isRefetching;
    return (
      <section aria-labelledby="voucher" className="mt-6 rounded-xl bg-surface px-4 py-4">
        <h2 id="voucher" className="text-lg font-bold text-ink">
          Tu voucher
        </h2>
        {voucher.isError && !stillTrying ? (
          <>
            <p className="mt-2 text-ink">
              El voucher sigue en emisión. Te llegará a tu correo en unos minutos.
            </p>
            <Button
              variant="secondary"
              className="mt-3"
              onClick={() => {
                void voucher.refetch();
              }}
            >
              <Icon name="refresh" />
              Volver a consultar
            </Button>
          </>
        ) : (
          <>
            <p role="status" className="mt-2 font-semibold text-ink-muted">
              Estamos emitiendo tu voucher…
            </p>
            <Skeleton className="mx-auto mt-3 aspect-square w-3/4" />
          </>
        )}
      </section>
    );
  }

  if (voucher.isError) {
    return (
      <section aria-labelledby="voucher" className="mt-6">
        <h2 id="voucher" className="text-lg font-bold text-ink">
          Tu voucher
        </h2>
        {voucher.error instanceof ApiError && voucher.error.status === 404 ? (
          <p className="mt-2 text-ink-muted">Aún no hay voucher para esta reserva.</p>
        ) : (
          <div className="mt-2">
            <ProblemNotice
              error={voucher.error}
              onRetry={() => {
                void voucher.refetch();
              }}
            />
          </div>
        )}
      </section>
    );
  }

  const data = voucher.data;
  const usable = data.status === 'ISSUED' && !used;
  // Solo un voucher vigente con token re-derivado se muestra como QR (ADR-0017 §2).
  const qrToken = usable ? data.token : null;
  const pdfReady = data.pdfStatus === 'READY';

  return (
    <section aria-labelledby="voucher" className="mt-6 rounded-xl bg-surface px-4 py-4">
      <h2 id="voucher" className="text-lg font-bold text-ink">
        Tu voucher
      </h2>
      <p className="mt-0.5 text-ink-muted first-letter:uppercase">{validLabel(data)}</p>

      {!usable && (
        <p className="mt-3 rounded-xl border-2 border-warning-border bg-warning-bg px-4 py-3 font-semibold text-warning-text">
          {data.status === 'ISSUED' ? STATUS_NOTE.CONSUMED : STATUS_NOTE[data.status]}
        </p>
      )}

      {qrToken !== null && (
        <div className="mx-auto mt-3 w-full max-w-72 rounded-lg bg-qr-paper p-2">
          <QrCode value={qrToken} label={`Código QR del voucher, ${validLabel(data)}`} />
        </div>
      )}
      {qrToken !== null && (
        // Justo bajo el código que agranda, no en una barra fija al fondo de la pantalla.
        <Button
          className="mt-3 w-full"
          onClick={() => {
            enterFullscreen();
            setFullscreen(true);
          }}
        >
          <Icon name="search" />
          Mostrar QR a pantalla completa
        </Button>
      )}
      {usable && data.token === null && (
        <p className="mt-3 flex items-start gap-2 text-ink">
          <Icon name="alert" className="mt-0.5 text-info" />
          El QR está en el PDF del voucher. Descárgalo para presentarlo el día del recorrido.
        </p>
      )}

      <p className="mt-4 text-center text-sm text-ink-muted">Código de contingencia</p>
      <p className="text-center font-mono text-2xl font-bold tracking-widest text-ink tabular-nums">
        {groupShortCode(data.shortCode)}
      </p>

      <Button
        variant="secondary"
        className="mt-4 w-full"
        disabled={!pdfReady || downloading}
        onClick={() => void downloadPdf()}
      >
        <Icon name="external" />
        {pdfReady
          ? downloading
            ? 'Preparando la descarga…'
            : 'Descargar voucher (PDF/A)'
          : 'Preparando el PDF…'}
      </Button>
      <p className="mt-2 text-sm text-ink-muted">
        Descárgalo para tenerlo sin conexión el día del recorrido.
      </p>
      {downloadError !== null && (
        <div className="mt-3">
          <ProblemNotice error={downloadError} />
        </div>
      )}

      {qrToken !== null && (
        <QrFullscreen
          open={fullscreen}
          onClose={() => {
            setFullscreen(false);
          }}
          token={qrToken}
          shortCode={data.shortCode}
          validLabel={validLabel(data)}
        />
      )}
    </section>
  );
}
