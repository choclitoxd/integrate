import { useState } from 'react';
import { Link } from 'react-router';

import { StatusBadge, TableSearchBar } from '@/features/admin';
import { formatPhoneDisplay, pluralize } from '@/shared/lib';
import { Button, buttonClassName, Icon, ProblemNotice, Select, Skeleton } from '@/shared/ui';

import type { ManagedHost } from '../api';
import { ActivationCodeIssuer } from '../components/ActivationCodeIssuer';
import { useManagedHosts } from '../queries';

const NOT_ACTIVE = {
  ENROLLMENT_MANAGER_NOT_ACTIVE:
    'Tu acreditación como gestor no está activa. Pide a la administración de Intégrate que la revise.',
};

const ACTIVATION_OPTIONS = [
  { value: 'ALL', label: 'Todos' },
  { value: 'ACTIVE', label: 'Activo' },
  { value: 'PENDING_RESET', label: 'Pendiente de activación' },
] as const;

function HostCard({ host }: { host: ManagedHost }) {
  const pending = host.credentialStatus === 'PENDING_RESET';
  // Las cuentas con correo real ya activas recuperan por correo (409): no se ofrece código.
  const canIssue = pending || host.emailIsSynthetic;
  return (
    <li className="grid min-w-0 content-start gap-3 rounded-xl border-2 border-border-subtle bg-canvas p-4">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <h2 className="min-w-0 text-lg font-bold break-words text-ink">{host.fullName}</h2>
        <StatusBadge tone={pending ? 'warning' : 'success'}>
          {pending ? 'Pendiente de activación' : 'Activo'}
        </StatusBadge>
      </div>
      <dl className="grid gap-1.5 text-ink">
        <div className="flex items-center gap-2">
          <dt className="sr-only">Documento</dt>
          <Icon name="file" size={18} className="shrink-0 text-ink-muted" />
          <dd className="font-mono tabular-nums">
            {host.docType} <span aria-hidden="true">••••</span>
            <span className="sr-only">terminado en </span>
            {host.docNumberLast4}
          </dd>
        </div>
        <div className="flex items-center gap-2">
          <dt className="sr-only">Celular</dt>
          <Icon name="user" size={18} className="shrink-0 text-ink-muted" />
          <dd className="tabular-nums">{formatPhoneDisplay(host.phone)}</dd>
        </div>
        <div className="flex items-start gap-2">
          <dt className="sr-only">Correo</dt>
          <Icon name="eye" size={18} className="mt-0.5 shrink-0 text-ink-muted" />
          <dd className="min-w-0 break-all text-ink-muted">
            {host.email}
            {host.emailIsSynthetic && ' (interno)'}
          </dd>
        </div>
      </dl>
      {canIssue && (
        <div>
          <ActivationCodeIssuer hostProfileId={host.hostProfileId} email={host.email} reissue />
        </div>
      )}
    </li>
  );
}

/** Anfitriones de la jurisdicción del gestor, sin datos de víctima (ADR-0018 §6). */
export function HostsPage() {
  // `search` es lo que se escribe; `filters.name` es lo que ya se consultó (al buscar).
  const [search, setSearch] = useState('');
  const [filters, setFilters] = useState({ name: '', activation: 'ALL' });
  const hosts = useManagedHosts(
    filters.name,
    filters.activation === 'ALL' ? '' : filters.activation,
  );
  const items = hosts.data?.pages.flatMap((page) => page.items) ?? [];
  const total = hosts.data?.pages[0]?.totalElements ?? 0;
  const filtered = filters.name !== '' || filters.activation !== 'ALL';

  function clearFilters() {
    setSearch('');
    setFilters({ name: '', activation: 'ALL' });
  }

  return (
    <>
      <title>Mis anfitriones · Intégrate</title>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h1 className="text-3xl font-bold text-brand">Mis anfitriones</h1>
          <p className="mt-2 max-w-prose text-ink-muted">
            Personas de tu jurisdicción. Entrega aquí el código para que activen su cuenta.
          </p>
        </div>
        <Link
          to="/gestor/anfitriones/enrolamiento"
          className={buttonClassName('primary', 'w-full sm:w-auto')}
        >
          <Icon name="plus" />
          Enrolar anfitrión
        </Link>
      </div>

      <section aria-labelledby="filtros-anfitriones" className="mt-6 rounded-xl bg-surface p-4">
        <h2 id="filtros-anfitriones" className="flex items-center gap-2 text-lg font-bold text-ink">
          <Icon name="filters" size={20} className="text-brand" />
          Filtrar anfitriones
        </h2>
        <div className="mt-4 grid items-end gap-4 md:grid-cols-[minmax(0,1fr)_minmax(0,18rem)]">
          <div>
            <p className="mb-1.5 font-semibold text-ink">Buscar por nombre</p>
            <TableSearchBar
              label="Buscar anfitrión por nombre"
              placeholder="Nombre del anfitrión"
              value={search}
              onChange={setSearch}
              onSubmitSearch={(name) => {
                setFilters((current) => ({ ...current, name }));
              }}
            />
          </div>
          <Select
            label="Estado de activación"
            name="activation"
            value={filters.activation}
            options={ACTIVATION_OPTIONS}
            onChange={(activation) => {
              setFilters((current) => ({ ...current, activation }));
            }}
          />
        </div>
        {filtered && (
          <div className="mt-4">
            <Button type="button" variant="secondary" onClick={clearFilters}>
              Limpiar filtros
            </Button>
          </div>
        )}
      </section>

      <div className="mt-6">
        {hosts.isPending && (
          <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3" aria-hidden="true">
            <Skeleton className="h-40" />
            <Skeleton className="h-40" />
            <Skeleton className="hidden h-40 xl:block" />
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
          <div className="grid justify-items-center gap-3 rounded-xl border-2 border-dashed border-border-subtle px-4 py-10 text-center">
            <span className="grid size-14 place-items-center rounded-full bg-brand-soft text-brand">
              <Icon name="users" size={28} />
            </span>
            <p className="text-lg font-bold text-ink">
              {filtered
                ? 'No hay anfitriones que coincidan con los filtros.'
                : 'Aún no tienes anfitriones enrolados.'}
            </p>
            <p className="max-w-prose text-ink-muted">
              {filtered
                ? 'Prueba con otro nombre o cambia el estado de activación.'
                : 'Enrola a la primera persona de tu jurisdicción para entregarle su código de activación.'}
            </p>
            {filtered && (
              <Button type="button" variant="secondary" onClick={clearFilters}>
                Limpiar filtros
              </Button>
            )}
          </div>
        )}
        {items.length > 0 && (
          <>
            <p className="mb-3 font-semibold text-ink" aria-live="polite">
              {pluralize(total, 'anfitrión', 'anfitriones')}
            </p>
            <ul aria-label="Anfitriones" className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
              {items.map((host) => (
                <HostCard key={host.hostProfileId} host={host} />
              ))}
            </ul>
          </>
        )}
        {hosts.hasNextPage && (
          <Button
            variant="secondary"
            className="mt-4 w-full sm:w-auto"
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
