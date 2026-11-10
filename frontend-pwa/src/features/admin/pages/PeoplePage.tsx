import { useState } from 'react';
import { Link, useSearchParams } from 'react-router';

import { Button, Icon, ProblemNotice, Select, Skeleton, type IconName } from '@/shared/ui';

import type { AdminLinkManager, LinkManagerStatus, ManagerAction } from '../api';
import { ManagerStatusDialog } from '../components/ManagerStatusDialog';
import { PersonSearch } from '../components/PersonSearch';
import {
  EmptyRow,
  Notice,
  PageHeader,
  Pager,
  StatusBadge,
  TableFrame,
  TableSearchBar,
} from '../components/parts';
import {
  CAPTION_CLASS,
  matchesSearchQuery,
  TABLE_CLASS,
  TD_CLASS,
  TH_CLASS,
} from '../components/tableStyles';
import { parsePersonSearchQuery } from '../forms';
import { formatInstant, MANAGER_FILTERS, MANAGER_STATUS } from '../labels';
import { useAssociationOptions, useHosts, useLinkManagers, useSearchUser } from '../queries';

import { NewUserSheet } from '../components/NewUserSheet';

const STATUSES = new Set<string>(['ACTIVE', 'SUSPENDED', 'REVOKED']);
const ALL = 'TODOS';

type PeopleView = 'todos' | 'turistas' | 'gestores' | 'anfitriones';

function viewFrom(raw: string | null): PeopleView {
  if (raw === 'turistas' || raw === 'gestores' || raw === 'anfitriones') return raw;
  return 'todos';
}

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

const ACTION_ICON: Readonly<Record<ManagerAction, IconName>> = {
  suspend: 'pause',
  reinstate: 'refresh',
  revoke: 'close',
};

function dayText(isoDate: string): string {
  return formatInstant(`${isoDate}T12:00:00-05:00`).split(',')[0] ?? isoDate;
}

function ManagersTable() {
  const [params, setParams] = useSearchParams();
  const status = statusFrom(params.get('estado'));
  const page = pageFrom(params);
  const managers = useLinkManagers(status, page);
  const lookup = useSearchUser();
  const [dialog, setDialog] = useState<{ manager: AdminLinkManager; action: ManagerAction } | null>(
    null,
  );
  const [result, setResult] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [resolvedUserId, setResolvedUserId] = useState<string | null | undefined>(undefined);
  const allItems = managers.data?.items ?? [];
  const items = allItems.filter((manager) => {
    if (resolvedUserId !== undefined) {
      return resolvedUserId !== null && manager.userId === resolvedUserId;
    }
    return matchesSearchQuery(query, [
      manager.fullName,
      manager.email,
      manager.associationName,
      manager.accreditedByName,
      MANAGER_STATUS[manager.accreditationStatus].text,
    ]);
  });
  const statusParam = status ?? ALL;

  function handleSearchChange(next: string) {
    setQuery(next);
    setResolvedUserId(undefined);
  }

  function handleSearchSubmit(submitted: string) {
    const parsed = parsePersonSearchQuery(submitted, 'CC');
    if (!parsed.ok) return;
    lookup.mutate(parsed.criterion, {
      onSuccess: (foundPage) => {
        setResolvedUserId(foundPage.items[0]?.userId ?? null);
      },
    });
  }

  return (
    <>
      <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
        <nav aria-label="Filtrar gestores por estado">
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
        <div className="w-full sm:max-w-xs">
          <TableSearchBar
            label="Buscar en gestores de enlace"
            value={query}
            onChange={handleSearchChange}
            onSubmitSearch={handleSearchSubmit}
          />
        </div>
      </div>

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
          <TableFrame title="Gestores de enlace" count={managers.data?.totalElements}>
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
                              <Icon name={ACTION_ICON[action]} />
                              <span className="hidden sm:inline">{ACTION_LABEL[action]}</span>
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
              const queryParams = new URLSearchParams(params);
              queryParams.set('vista', 'gestores');
              if (next > 0) queryParams.set('pagina', String(next + 1));
              else queryParams.delete('pagina');
              setParams(queryParams);
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
  const lookup = useSearchUser();
  const [query, setQuery] = useState('');
  const [resolvedUserId, setResolvedUserId] = useState<string | null | undefined>(undefined);
  const allItems = hosts.data?.items ?? [];
  const items = allItems.filter((host) => {
    if (resolvedUserId !== undefined) {
      return resolvedUserId !== null && host.userId === resolvedUserId;
    }
    return matchesSearchQuery(query, [
      host.fullName,
      host.email,
      host.associationName,
      host.verified ? 'Verificado' : 'Sin verificar',
    ]);
  });
  const options = [
    { value: '', label: 'Todas las asociaciones' },
    ...(associations.data?.items ?? []).map((association) => ({
      value: association.id,
      label: association.legalName,
    })),
  ];

  function handleSearchChange(next: string) {
    setQuery(next);
    setResolvedUserId(undefined);
  }

  function handleSearchSubmit(submitted: string) {
    const parsed = parsePersonSearchQuery(submitted, 'CC');
    if (!parsed.ok) return;
    lookup.mutate(parsed.criterion, {
      onSuccess: (foundPage) => {
        setResolvedUserId(foundPage.items[0]?.userId ?? null);
      },
    });
  }

  return (
    <>
      <div className="mt-4 grid items-end gap-3 sm:grid-cols-[minmax(0,24rem)_minmax(0,20rem)] sm:justify-between">
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
        <TableSearchBar
          label="Buscar en anfitriones"
          value={query}
          onChange={handleSearchChange}
          onSubmitSearch={handleSearchSubmit}
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
          <TableFrame title="Anfitriones" count={hosts.data?.totalElements}>
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
              const queryParams = new URLSearchParams(params);
              queryParams.set('vista', 'anfitriones');
              if (next > 0) queryParams.set('pagina', String(next + 1));
              else queryParams.delete('pagina');
              setParams(queryParams);
            }}
          />
        )}
      </div>
    </>
  );
}

const PEOPLE_VIEWS: readonly [PeopleView, string][] = [
  ['todos', 'Todos'],
  ['turistas', 'Turistas'],
  ['gestores', 'Gestores de enlace'],
  ['anfitriones', 'Anfitriones'],
];

/** Personas de la consola: selector de rol (Todos, Turistas, Gestores de enlace, Anfitriones) con buscador y altas (ADR-0043, ADR-0048). */
export function PeoplePage() {
  const [params] = useSearchParams();
  const view = viewFrom(params.get('vista'));
  const [newUserOpen, setNewUserOpen] = useState(false);
  const [newNotice, setNewNotice] = useState<string | null>(null);

  const actionText =
    view === 'gestores'
      ? 'Nuevo gestor'
      : view === 'anfitriones'
        ? 'Nuevo anfitrión'
        : 'Nuevo usuario';

  return (
    <>
      <title>Personas · Administración · Intégrate</title>
      <PageHeader
        title="Personas"
        description="Elige el grupo a consultar, busca por correo o documento y administra acreditaciones."
        actions={
          <Button
            onClick={() => {
              setNewNotice(null);
              setNewUserOpen(true);
            }}
          >
            <Icon name="plus" />
            <span className="hidden sm:inline">{actionText}</span>
          </Button>
        }
      />

      {newNotice && (
        <div className="mt-4">
          <Notice tone="success">{newNotice}</Notice>
        </div>
      )}

      <section aria-label="Gestión de personas" className="mt-6">
        <div className="flex flex-col gap-2">
          <p className="text-sm font-semibold text-ink-muted">Roles</p>
          <nav aria-label="Roles">
            <ul className="flex flex-wrap gap-2">
              {PEOPLE_VIEWS.map(([value, text]) => (
                <li key={value}>
                  <Link
                    to={value === 'todos' ? '?vista=todos' : `?vista=${value}`}
                    aria-current={view === value ? 'true' : undefined}
                    className="inline-flex min-h-11 items-center rounded-lg border-2 border-border bg-canvas px-4 font-semibold text-ink hover:bg-surface-subtle aria-[current=true]:border-brand aria-[current=true]:bg-brand-soft aria-[current=true]:text-brand"
                  >
                    {text}
                  </Link>
                </li>
              ))}
            </ul>
          </nav>
        </div>

        <div className="mt-4">
          <Notice tone="info" role="note">
            Cada búsqueda o listado consultado aquí queda registrado en la bitácora de auditoría.
          </Notice>
        </div>

        {view === 'todos' && <PersonSearch roleFilter={null} />}
        {view === 'turistas' && <PersonSearch roleFilter="TOURIST" />}
        {view === 'gestores' && <ManagersTable />}
        {view === 'anfitriones' && <HostsTable />}
      </section>

      {newUserOpen && (
        <NewUserSheet
          onClose={() => {
            setNewUserOpen(false);
          }}
          onCreated={(fullName) => {
            setNewUserOpen(false);
            setNewNotice(`Se creó la cuenta de «${fullName}».`);
          }}
        />
      )}
    </>
  );
}
