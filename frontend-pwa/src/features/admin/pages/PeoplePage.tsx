import { useState } from 'react';
import { Link, useSearchParams } from 'react-router';

import { Button, ProblemNotice, Select, Skeleton } from '@/shared/ui';

import type { AdminLinkManager, LinkManagerStatus, ManagerAction } from '../api';
import { ManagerStatusDialog } from '../components/ManagerStatusDialog';
import { PersonSearch } from '../components/PersonSearch';
import { EmptyRow, Notice, PageHeader, Pager, StatusBadge, TableFrame } from '../components/parts';
import { CAPTION_CLASS, TABLE_CLASS, TD_CLASS, TH_CLASS } from '../components/tableStyles';
import { formatInstant, MANAGER_FILTERS, MANAGER_STATUS } from '../labels';
import { useAssociationOptions, useHosts, useLinkManagers } from '../queries';

const STATUSES = new Set<string>(['ACTIVE', 'SUSPENDED', 'REVOKED']);
const ALL = 'TODOS';

/** `?estado=`: sin parámetro se abren los gestores vigentes; "TODOS" quita el filtro. */
function statusFrom(raw: string | null): LinkManagerStatus | null {
  if (raw === ALL) return null;
  return raw !== null && STATUSES.has(raw) ? (raw as LinkManagerStatus) : 'ACTIVE';
}

function pageFrom(params: URLSearchParams): number {
  return Math.max(Number.parseInt(params.get('pagina') ?? '1', 10) || 1, 1) - 1;
}

const ACTIONS: Readonly<Record<LinkManagerStatus, readonly ManagerAction[]>> = {
  ACTIVE: ['suspend', 'revoke'],
  SUSPENDED: ['reinstate', 'revoke'],
  REVOKED: [],
};

const ACTION_LABEL: Readonly<Record<ManagerAction, string>> = {
  suspend: 'Suspender',
  reinstate: 'Reactivar',
  revoke: 'Revocar',
};

function dayText(isoDate: string): string {
  return formatInstant(`${isoDate}T12:00:00-05:00`).split(',')[0] ?? isoDate;
}

function ManagersTable() {
  const [params, setParams] = useSearchParams();
  const status = statusFrom(params.get('estado'));
  const page = pageFrom(params);
  const managers = useLinkManagers(status, page);
  const [dialog, setDialog] = useState<{ manager: AdminLinkManager; action: ManagerAction } | null>(
    null,
  );
  const [result, setResult] = useState<string | null>(null);
  const items = managers.data?.items ?? [];
  const statusParam = status ?? ALL;

  return (
    <>
      <nav aria-label="Filtrar gestores por estado" className="mt-4">
        <ul className="flex flex-wrap gap-2">
          {MANAGER_FILTERS.map((filter) => {
            const value = filter.value ?? ALL;
            return (
              <li key={value}>
                <Link
                  to={`?vista=gestores&estado=${value}`}
                  aria-current={value === statusParam ? 'true' : undefined}
                  onClick={() => {
                    setResult(null);
                  }}
                  className="inline-flex min-h-11 items-center rounded-full border-2 border-border bg-canvas px-4 font-semibold text-ink hover:bg-surface-subtle aria-[current=true]:border-brand aria-[current=true]:bg-brand aria-[current=true]:text-on-brand"
                >
                  {filter.text}
                </Link>
              </li>
            );
          })}
        </ul>
      </nav>

      {result && (
        <div className="mt-4">
          <Notice tone="success">{result}</Notice>
        </div>
      )}

      <div className="mt-4">
        {managers.isError ? (
          <ProblemNotice
            error={managers.error}
            onRetry={() => {
              void managers.refetch();
            }}
          />
        ) : (
          <TableFrame>
            <table className={TABLE_CLASS}>
              <caption className={CAPTION_CLASS}>
                Gestores de enlace
                {managers.data && (
                  <span className="font-normal text-ink-muted">
                    {' '}
                    · {managers.data.totalElements}
                  </span>
                )}
              </caption>
              <thead>
                <tr>
                  <th scope="col" className={TH_CLASS}>
                    Persona
                  </th>
                  <th scope="col" className={TH_CLASS}>
                    Asociación
                  </th>
                  <th scope="col" className={TH_CLASS}>
                    Estado
                  </th>
                  <th scope="col" className={TH_CLASS}>
                    Acreditado
                  </th>
                  <th scope="col" className={TH_CLASS}>
                    Acciones
                  </th>
                </tr>
              </thead>
              <tbody>
                {managers.isPending && (
                  <EmptyRow columns={5}>
                    <span role="status">Cargando gestores…</span>
                    <Skeleton className="mx-auto mt-3 h-6 w-2/3" />
                  </EmptyRow>
                )}
                {managers.isSuccess && items.length === 0 && (
                  <EmptyRow columns={5}>No hay gestores en este estado.</EmptyRow>
                )}
                {items.map((manager) => {
                  const label = MANAGER_STATUS[manager.accreditationStatus];
                  return (
                    <tr key={manager.linkManagerId}>
                      <th scope="row" className={`${TD_CLASS} font-semibold`}>
                        {manager.fullName}
                        {manager.email && (
                          <span className="block text-sm font-normal break-all text-ink-muted">
                            {manager.email}
                          </span>
                        )}
                      </th>
                      <td className={TD_CLASS}>{manager.associationName ?? '—'}</td>
                      <td className={TD_CLASS}>
                        <StatusBadge tone={label.tone}>{label.text}</StatusBadge>
                      </td>
                      <td className={`${TD_CLASS} text-sm`}>
                        {dayText(manager.accreditedOn)}
                        {manager.accreditedByName && (
                          <span className="block text-ink-muted">
                            por {manager.accreditedByName}
                          </span>
                        )}
                      </td>
                      <td className={TD_CLASS}>
                        <div className="flex flex-wrap gap-2">
                          {ACTIONS[manager.accreditationStatus].map((action) => (
                            <Button
                              key={action}
                              variant="secondary"
                              aria-label={`${ACTION_LABEL[action]} a ${manager.fullName}`}
                              onClick={() => {
                                setResult(null);
                                setDialog({ manager, action });
                              }}
                            >
                              {ACTION_LABEL[action]}
                            </Button>
                          ))}
                          {ACTIONS[manager.accreditationStatus].length === 0 && (
                            <span className="text-ink-muted">Sin acciones</span>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </TableFrame>
        )}
        {managers.data && (
          <Pager
            label="gestores"
            page={managers.data.page}
            totalPages={managers.data.totalPages}
            totalElements={managers.data.totalElements}
            onChange={(next) => {
              const query = new URLSearchParams(params);
              query.set('vista', 'gestores');
              if (next > 0) query.set('pagina', String(next + 1));
              else query.delete('pagina');
              setParams(query);
            }}
          />
        )}
      </div>

      {dialog && (
        <ManagerStatusDialog
          key={`${dialog.manager.linkManagerId}-${dialog.action}`}
          manager={dialog.manager}
          action={dialog.action}
          onClose={() => {
            setDialog(null);
          }}
          onDone={(message) => {
            setDialog(null);
            setResult(message);
          }}
        />
      )}
    </>
  );
}

function HostsTable() {
  const [params, setParams] = useSearchParams();
  const associationId = params.get('asociacion');
  const page = pageFrom(params);
  const hosts = useHosts(associationId, page);
  const associations = useAssociationOptions();
  const items = hosts.data?.items ?? [];
  const options = [
    { value: '', label: 'Todas las asociaciones' },
    ...(associations.data?.items ?? []).map((association) => ({
      value: association.id,
      label: association.legalName,
    })),
  ];

  return (
    <>
      <div className="mt-4 max-w-md">
        <Select
          label="Filtrar por asociación"
          size="lg"
          searchable
          searchPlaceholder="Todas las asociaciones"
          value={associationId ?? ''}
          options={options}
          onChange={(value) => {
            setParams(
              value ? { vista: 'anfitriones', asociacion: value } : { vista: 'anfitriones' },
            );
          }}
        />
      </div>

      <div className="mt-4">
        {hosts.isError ? (
          <ProblemNotice
            error={hosts.error}
            onRetry={() => {
              void hosts.refetch();
            }}
          />
        ) : (
          <TableFrame>
            <table className={TABLE_CLASS}>
              <caption className={CAPTION_CLASS}>
                Anfitriones
                {hosts.data && (
                  <span className="font-normal text-ink-muted"> · {hosts.data.totalElements}</span>
                )}
              </caption>
              <thead>
                <tr>
                  <th scope="col" className={TH_CLASS}>
                    Persona
                  </th>
                  <th scope="col" className={TH_CLASS}>
                    Asociación
                  </th>
                  <th scope="col" className={TH_CLASS}>
                    Verificado
                  </th>
                  <th scope="col" className={TH_CLASS}>
                    Alta
                  </th>
                </tr>
              </thead>
              <tbody>
                {hosts.isPending && (
                  <EmptyRow columns={4}>
                    <span role="status">Cargando anfitriones…</span>
                    <Skeleton className="mx-auto mt-3 h-6 w-2/3" />
                  </EmptyRow>
                )}
                {hosts.isSuccess && items.length === 0 && (
                  <EmptyRow columns={4}>No hay anfitriones para este filtro.</EmptyRow>
                )}
                {items.map((host) => (
                  <tr key={host.hostProfileId}>
                    <th scope="row" className={`${TD_CLASS} font-semibold`}>
                      {host.fullName}
                      {host.email && (
                        <span className="block text-sm font-normal break-all text-ink-muted">
                          {host.email}
                          {host.emailIsSynthetic && ' (interno)'}
                        </span>
                      )}
                    </th>
                    <td className={TD_CLASS}>{host.associationName ?? '—'}</td>
                    <td className={TD_CLASS}>
                      <StatusBadge tone={host.verified ? 'success' : 'neutral'}>
                        {host.verified ? 'Verificado' : 'Sin verificar'}
                      </StatusBadge>
                    </td>
                    <td className={`${TD_CLASS} text-sm`}>{formatInstant(host.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </TableFrame>
        )}
        {hosts.data && (
          <Pager
            label="anfitriones"
            page={hosts.data.page}
            totalPages={hosts.data.totalPages}
            totalElements={hosts.data.totalElements}
            onChange={(next) => {
              const query = new URLSearchParams(params);
              query.set('vista', 'anfitriones');
              if (next > 0) query.set('pagina', String(next + 1));
              else query.delete('pagina');
              setParams(query);
            }}
          />
        )}
      </div>
    </>
  );
}

/** Personas de la consola: búsqueda exacta, altas y las listas de gestores y anfitriones (ADR-0043 §1). */
export function PeoplePage() {
  const [params] = useSearchParams();
  const view = params.get('vista') === 'anfitriones' ? 'anfitriones' : 'gestores';

  return (
    <>
      <title>Personas · Administración · Intégrate</title>
      <PageHeader
        title="Personas"
        description="Convierte a una persona en anfitrión, acredita gestores de enlace y gestiona su estado."
      />

      <div className="mt-6 grid gap-6">
        <PersonSearch />

        <section aria-labelledby="listas-titulo">
          <h2 id="listas-titulo" className="text-xl font-bold text-ink">
            Gestores y anfitriones
          </h2>
          <nav aria-label="Lista" className="mt-3">
            <ul className="flex flex-wrap gap-2">
              {(
                [
                  ['gestores', 'Gestores de enlace'],
                  ['anfitriones', 'Anfitriones'],
                ] as const
              ).map(([value, text]) => (
                <li key={value}>
                  <Link
                    to={`?vista=${value}`}
                    aria-current={view === value ? 'true' : undefined}
                    className="inline-flex min-h-11 items-center rounded-lg border-2 border-border bg-canvas px-4 font-semibold text-ink hover:bg-surface-subtle aria-[current=true]:border-brand aria-[current=true]:bg-brand-soft aria-[current=true]:text-brand"
                  >
                    {text}
                  </Link>
                </li>
              ))}
            </ul>
          </nav>
          <div className="mt-3">
            <Notice tone="info" role="note">
              Ver estas listas también queda registrado en la bitácora de auditoría.
            </Notice>
          </div>
          {view === 'gestores' ? <ManagersTable /> : <HostsTable />}
        </section>
      </div>
    </>
  );
}
