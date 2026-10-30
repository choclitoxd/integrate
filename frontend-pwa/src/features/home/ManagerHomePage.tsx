import { useState } from 'react';
import { Link } from 'react-router';

import { useHttpClient } from '@/shared/api';
import { readOfflineMeta, useOfflineStatus } from '@/shared/offline';
import { Button, Icon, type IconName } from '@/shared/ui';

const SHORTCUTS: readonly { to: string; title: string; body: string; icon: IconName }[] = [
  {
    to: '/gestor/validar',
    title: 'Validar QR en campo',
    body: 'Verifica la firma criptográfica del voucher y registra el ingreso sin red.',
    icon: 'qr',
  },
  {
    to: '/gestor/contingencia',
    title: 'Contingencia manual',
    body: 'Canjea un voucher dañado por código de 8 caracteres o búscalo en el padrón.',
    icon: 'ticket',
  },
  {
    to: '/gestor/cola',
    title: 'Cola de sincronización',
    body: 'Consulta las asistencias validadas en este teléfono y envíalas al servidor.',
    icon: 'clock',
  },
  {
    to: '/gestor/anfitriones/enrolamiento',
    title: 'Enrolar anfitrión',
    body: 'Registra a una persona de la comunidad con su autorización firmada.',
    icon: 'plus',
  },
  {
    to: '/gestor/anfitriones',
    title: 'Mis anfitriones',
    body: 'Consulta su estado y entrega el código para activar la cuenta.',
    icon: 'users',
  },
];

function formatMegabytes(bytes: number): string {
  const mb = bytes / (1024 * 1024);
  return new Intl.NumberFormat('es-CO', {
    minimumFractionDigits: mb > 0 && mb < 0.1 ? 2 : 1,
    maximumFractionDigits: 2,
  }).format(mb);
}

/**
 * Panel del Gestor de Enlace con descarga preventiva del padrón y diagnóstico offline
 * (T-2.8, T-2.9, ADR-0036 §5, ADR-0037 §1.3).
 */
export function ManagerHomePage() {
  const http = useHttpClient();
  const { online, diagnostics, pendingOutboxCount, rosterCount } = useOfflineStatus();
  const [downloading, setDownloading] = useState(false);
  const [refreshingRevocations, setRefreshingRevocations] = useState(false);
  const [rosterNotice, setRosterNotice] = useState<{
    readonly tone: 'success' | 'danger';
    readonly text: string;
  } | null>(null);

  const rosterMeta = readOfflineMeta().rosterMeta;

  const handleDownloadRoster = async () => {
    if (downloading) return;
    setDownloading(true);
    setRosterNotice(null);
    try {
      const { downloadAndStoreRoster } = await import('@/features/offline');
      const res = await downloadAndStoreRoster(http);
      setRosterNotice({
        tone: 'success',
        text: `Padrón descargado para ${res.meta.rosterDate}: ${String(res.vouchersCount)} ${
          res.vouchersCount === 1 ? 'voucher' : 'vouchers'
        } y ${String(res.signingKeysCount)} ${
          res.signingKeysCount === 1 ? 'clave pública' : 'claves públicas'
        } listos para operar sin red.`,
      });
    } catch (err) {
      setRosterNotice({
        tone: 'danger',
        text:
          err instanceof Error
            ? err.message
            : 'No se pudo descargar el padrón de la jornada. Verifica tu conexión.',
      });
    } finally {
      setDownloading(false);
    }
  };

  const handleRefreshRevocations = async () => {
    if (refreshingRevocations) return;
    setRefreshingRevocations(true);
    setRosterNotice(null);
    try {
      const { refreshRevocationDelta } = await import('@/features/offline');
      const res = await refreshRevocationDelta(http);
      setRosterNotice({
        tone: 'success',
        text: `Lista de revocación actualizada (${String(res.addedCount)} ${
          res.addedCount === 1 ? 'novedad incorporada' : 'novedades incorporadas'
        }).`,
      });
    } catch (err) {
      setRosterNotice({
        tone: 'danger',
        text:
          err instanceof Error
            ? err.message
            : 'No se pudo actualizar la lista de revocación. Intenta de nuevo en línea.',
      });
    } finally {
      setRefreshingRevocations(false);
    }
  };

  return (
    <>
      <title>Panel de jornada · Intégrate</title>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h1 className="text-3xl font-bold text-brand">Panel de jornada</h1>
        <p
          role="status"
          className={`inline-flex min-h-9 shrink-0 items-center gap-1.5 rounded-full border-2 px-3 text-sm font-bold ${
            online
              ? 'border-success-border bg-success-bg text-success-text'
              : 'border-warning-border bg-warning-bg text-warning-text'
          }`}
        >
          <Icon name={online ? 'wifi' : 'wifiOff'} size={18} />
          {online ? 'En línea' : 'Sin conexión · Modo campo'}
        </p>
      </div>

      <p className="mt-2 text-ink-muted">
        Administra tu jornada comunitaria, descarga el padrón antes de salir a terreno y verifica el
        almacenamiento en este teléfono.
      </p>

      <section
        aria-labelledby="padron-jornada-heading"
        className="mt-6 rounded-xl border-2 border-border-subtle bg-surface px-4 py-4"
      >
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 id="padron-jornada-heading" className="text-lg font-bold text-ink">
            Padrón operativo de la jornada
          </h2>
          <Icon name="download" size={20} className="text-brand" />
        </div>

        <p className="mt-1 text-sm text-ink-muted">
          {rosterMeta
            ? `Padrón activo para la fecha ${rosterMeta.rosterDate} (${String(rosterCount)} ${
                rosterCount === 1 ? 'voucher' : 'vouchers'
              }).`
            : 'Aún no has descargado el padrón de reservas de hoy en este dispositivo.'}
        </p>

        {rosterNotice !== null && (
          <p
            role={rosterNotice.tone === 'danger' ? 'alert' : 'status'}
            className={`mt-3 rounded-lg border-2 px-3 py-2 text-sm font-semibold ${
              rosterNotice.tone === 'success'
                ? 'border-success-border bg-success-bg text-success-text'
                : 'border-danger-border bg-danger-bg text-danger-text'
            }`}
          >
            {rosterNotice.text}
          </p>
        )}

        <div className="mt-4 grid gap-3 sm:grid-cols-2">
          <Button
            type="button"
            disabled={!online || downloading}
            onClick={() => void handleDownloadRoster()}
          >
            {downloading ? 'Descargando padrón…' : 'Descargar padrón de hoy'}
          </Button>
          <Button
            type="button"
            variant="secondary"
            disabled={!online || refreshingRevocations}
            onClick={() => void handleRefreshRevocations()}
          >
            {refreshingRevocations ? 'Actualizando…' : 'Actualizar revocaciones'}
          </Button>
        </div>
      </section>

      <section
        aria-labelledby="estado-offline-heading"
        className="mt-6 rounded-xl border-2 border-border-subtle bg-surface px-4 py-4"
      >
        <div className="flex items-center justify-between gap-2">
          <h2 id="estado-offline-heading" className="text-lg font-bold text-ink">
            Almacenamiento en el teléfono
          </h2>
          <span className="text-sm font-bold text-ink-muted">
            {formatMegabytes(diagnostics.usageBytes)} MB / 10 MB
          </span>
        </div>

        <dl className="mt-3 grid grid-cols-2 gap-3 text-sm">
          <div className="rounded-lg border border-border-subtle bg-canvas px-3 py-2">
            <dt className="text-ink-muted">Padrón en dispositivo</dt>
            <dd className="mt-0.5 text-base font-bold text-ink">
              {rosterCount} {rosterCount === 1 ? 'voucher' : 'vouchers'}
            </dd>
          </div>
          <div className="rounded-lg border border-border-subtle bg-canvas px-3 py-2">
            <dt className="text-ink-muted">Por sincronizar</dt>
            <dd className="mt-0.5 text-base font-bold text-ink">
              {pendingOutboxCount} {pendingOutboxCount === 1 ? 'pendiente' : 'pendientes'}
            </dd>
          </div>
        </dl>

        {diagnostics.persisted === false && (
          <p
            role="status"
            className="mt-3 flex items-start gap-2 rounded-lg border-2 border-warning-border bg-warning-bg px-3 py-2 text-sm font-semibold text-warning-text"
          >
            <Icon name="alert" size={18} className="mt-0.5 shrink-0" />
            <span>
              Almacenamiento volátil: libera espacio en el teléfono para evitar que el navegador
              borre el padrón desconectado.
            </span>
          </p>
        )}

        {diagnostics.nearLimit && (
          <p
            role="alert"
            className="mt-3 flex items-start gap-2 rounded-lg border-2 border-danger-border bg-danger-bg px-3 py-2 text-sm font-semibold text-danger-text"
          >
            <Icon name="alert" size={18} className="mt-0.5 shrink-0" />
            <span>
              El espacio local está cerca del límite de 10 MB. Conéctate a la red para sincronizar y
              depurar registros antiguos.
            </span>
          </p>
        )}
      </section>

      <ul className="mt-6 grid gap-3">
        {SHORTCUTS.map((item) => (
          <li key={item.to}>
            <Link
              to={item.to}
              className="flex min-h-11 items-start gap-3 rounded-xl border-2 border-border-subtle bg-canvas px-4 py-4 hover:border-brand hover:bg-brand-soft"
            >
              <span className="rounded-lg bg-brand-soft p-2 text-brand">
                <Icon name={item.icon} size={24} />
              </span>
              <span className="min-w-0 flex-1">
                <span className="block text-lg font-bold text-ink">{item.title}</span>
                <span className="mt-0.5 block text-ink-muted">{item.body}</span>
              </span>
              <Icon name="chevronRight" className="mt-1 text-ink-muted" />
            </Link>
          </li>
        ))}
      </ul>
    </>
  );
}
