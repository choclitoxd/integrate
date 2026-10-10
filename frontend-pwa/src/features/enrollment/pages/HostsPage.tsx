import { Link } from 'react-router';

import { pluralize } from '@/shared/lib';
import { buttonClassName, Button, Icon, ProblemNotice, Skeleton } from '@/shared/ui';

import type { ManagedHost } from '../api';
import { ActivationCodeIssuer } from '../components/ActivationCodeIssuer';
import { useManagedHosts } from '../queries';

const NOT_ACTIVE = {
  ENROLLMENT_MANAGER_NOT_ACTIVE:
    'Tu acreditación como gestor no está activa. Pide a la administración de Intégrate que la revise.',
};

/** "+573001234567" → "+57 300 123 4567"; otros países quedan como llegan. */
function formatPhone(phone: string): string {
  const match = /^\+57(\d{3})(\d{3})(\d{4})$/.exec(phone);
  return match ? `+57 ${match[1] ?? ''} ${match[2] ?? ''} ${match[3] ?? ''}` : phone;
}

function HostCard({ host }: { host: ManagedHost }) {
  const pending = host.credentialStatus === 'PENDING_RESET';
  // Las cuentas con correo real ya activas recuperan por correo (409): no se ofrece código.
  const canIssue = pending || host.emailIsSynthetic;
  return (
    <li className="rounded-xl border-2 border-border-subtle bg-canvas px-4 py-4">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <h2 className="text-lg font-bold text-ink">{host.fullName}</h2>
        <span
          className={`inline-flex items-center gap-1 rounded-full border-2 px-2.5 py-0.5 text-sm font-bold ${
            pending
              ? 'border-warning-border bg-warning-bg text-warning-text'
              : 'border-success-border bg-success-bg text-success-text'
          }`}
        >
          <Icon name={pending ? 'clock' : 'check'} size={16} />
          {pending ? 'Pendiente de activación' : 'Activo'}
        </span>
      </div>
      <dl className="mt-2 grid gap-1 text-ink">
        <div className="flex gap-2">
          <dt className="sr-only">Documento</dt>
          <dd className="font-mono tabular-nums">
            {host.docType} <span aria-hidden="true">••••</span>
            <span className="sr-only">terminado en </span>
            {host.docNumberLast4}
          </dd>
        </div>
        <div className="flex gap-2">
          <dt className="sr-only">Celular</dt>
          <dd>{formatPhone(host.phone)}</dd>
        </div>
        <div className="flex gap-2">
          <dt className="sr-only">Correo</dt>
          <dd className="break-all text-ink-muted">
            {host.email}
            {host.emailIsSynthetic && ' (interno)'}
          </dd>
        </div>
      </dl>
      {canIssue && (
        <div className="mt-3">
          <ActivationCodeIssuer hostProfileId={host.hostProfileId} email={host.email} reissue />
        </div>
      )}
    </li>
  );
}

/** Anfitriones de la jurisdicción del gestor, sin datos de víctima (ADR-0018 §6). */
export function HostsPage() {
  const hosts = useManagedHosts();
  const items = hosts.data?.pages.flatMap((page) => page.items) ?? [];
  const total = hosts.data?.pages[0]?.totalElements ?? 0;

  return (
    <>
      <title>Mis anfitriones · Intégrate</title>
      <h1 className="text-3xl font-bold text-brand">Mis anfitriones</h1>
      <p className="mt-2 text-ink-muted">
        Personas de tu jurisdicción. Entrega aquí el código para que activen su cuenta.
      </p>
      <Link
        to="/gestor/anfitriones/enrolamiento"
        className={buttonClassName('primary', 'mt-4 w-full')}
      >
        <Icon name="plus" />
        Enrolar anfitrión
      </Link>

      <div className="mt-6">
        {hosts.isPending && (
          <div className="grid gap-3" aria-hidden="true">
            <Skeleton className="h-32" />
            <Skeleton className="h-32" />
          </div>
        )}
        {hosts.isError && (
          <ProblemNotice
            error={hosts.error}
            messages={NOT_ACTIVE}
            onRetry={() => {
              void hosts.refetch();
            }}
          />
        )}
        {hosts.isSuccess && items.length === 0 && (
          <p className="rounded-xl bg-surface px-4 py-6 text-center text-ink-muted">
            Aún no tienes anfitriones enrolados.
          </p>
        )}
        {items.length > 0 && (
          <>
            <p className="mb-3 font-semibold text-ink" aria-live="polite">
              {pluralize(total, 'anfitrión', 'anfitriones')}
            </p>
            <ul aria-label="Anfitriones" className="grid gap-3">
              {items.map((host) => (
                <HostCard key={host.hostProfileId} host={host} />
              ))}
            </ul>
          </>
        )}
        {hosts.hasNextPage && (
          <Button
            variant="secondary"
            className="mt-4 w-full"
            disabled={hosts.isFetchingNextPage}
            onClick={() => void hosts.fetchNextPage()}
          >
            {hosts.isFetchingNextPage ? 'Cargando…' : 'Ver más'}
          </Button>
        )}
      </div>
    </>
  );
}
