import { useId, useRef, useState, type SubmitEvent } from 'react';
import { useSearchParams } from 'react-router';
import { useQueryClient } from '@tanstack/react-query';

import { Button, Icon, ProblemNotice, Skeleton } from '@/shared/ui';

import type { AdminUser, UserRole } from '../api';
import { parsePersonSearchQuery } from '../forms';
import { roleName } from '../labels';
import { adminKeys, useSearchUser, useUsers } from '../queries';
import { AssignDialog, type AssignKind, type AssignResult } from './AssignDialog';
import { ManageUserSheet } from './ManageUserSheet';
import { EmptyRow, Notice, Pager, StatusBadge, TableFrame } from './parts';
import {
  CAPTION_CLASS,
  matchesSearchQuery,
  SEARCH_BUTTON_CLASS,
  SEARCH_FORM_CLASS,
  SEARCH_INPUT_CLASS,
  TABLE_CLASS,
  TD_CLASS,
  TH_CLASS,
} from './tableStyles';

const RATE_LIMITED = {
  ADMIN_LOOKUP_RATE_LIMITED:
    'Hiciste demasiadas búsquedas seguidas (el límite es 20 por minuto). Espera un momento e inténtalo de nuevo.',
};

const USER_ROLE_FILTERS: readonly { value: string | null; text: string }[] = [
  { value: null, text: 'Todos' },
  { value: 'TOURIST', text: 'Turistas' },
  { value: 'HOST', text: 'Anfitriones' },
  { value: 'LINK_MANAGER', text: 'Gestores' },
];

function UserRow({ user, onManage }: { user: AdminUser; onManage: (user: AdminUser) => void }) {
  return (
    <tr>
      <th scope="row" className={`${TD_CLASS} font-semibold`}>
        {user.fullName}
      </th>
      <td className={`${TD_CLASS} break-all`}>
        {user.email}
        {user.emailMasked && (
          <span className="block text-sm text-ink-muted">
            (parcialmente oculto: la buscaste por documento)
          </span>
        )}
      </td>
      <td className={TD_CLASS}>
        <ul aria-label="Roles vigentes" className="flex flex-wrap gap-2">
          {user.roles.map((role) => (
            <li key={role}>
              <StatusBadge tone="info">{roleName(role)}</StatusBadge>
            </li>
          ))}
        </ul>
      </td>
      <td className={TD_CLASS}>
        <Button
          variant="secondary"
          aria-label={`Gestionar a ${user.fullName}`}
          onClick={() => {
            onManage(user);
          }}
        >
          <Icon name="edit" />
          <span className="hidden sm:inline">Gestionar</span>
        </Button>
      </td>
    </tr>
  );
}

interface PersonSearchProps {
  readonly roleFilter?: 'TOURIST' | null;
}

/**
 * Vista de Usuarios en Personas (ADR-0043, ADR-0048): carga automática de cuentas por rol,
 * más buscador acoplado a la derecha que acepta correo o documento sin cambiar de filtro.
 */
export function PersonSearch({ roleFilter = null }: PersonSearchProps) {
  const [params, setParams] = useSearchParams();
  const queryClient = useQueryClient();
  const search = useSearchUser();
  const formRef = useRef<HTMLFormElement>(null);
  const inputId = useId();
  const errorId = useId();

  const [query, setQuery] = useState('');
  const [activeRole, setActiveRole] = useState<string | null>(roleFilter);
  const [error, setError] = useState<string | undefined>();
  const [found, setFound] = useState<AdminUser | null | undefined>(undefined);
  const [managingUser, setManagingUser] = useState<AdminUser | null>(null);
  const [assign, setAssign] = useState<AssignKind | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const page = Math.max(Number.parseInt(params.get('pagina') ?? '1', 10) || 1, 1) - 1;
  const users = useUsers((activeRole ?? roleFilter) as UserRole | null, page);

  const allItems = users.data?.items ?? [];
  const filteredItems = allItems.filter((user) =>
    matchesSearchQuery(query, [user.fullName, user.email, ...user.roles]),
  );

  const matchesRoleFilter =
    found !== null &&
    found !== undefined &&
    (activeRole === null || found.roles.includes(activeRole));

  const visibleCount =
    found !== undefined
      ? matchesRoleFilter
        ? 1
        : 0
      : users.data
        ? query
          ? filteredItems.length
          : users.data.totalElements
        : undefined;

  function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    const parsed = parsePersonSearchQuery(query);
    if (!parsed.ok) {
      setError(parsed.error);
      (formRef.current?.elements.namedItem('query') as HTMLElement | null)?.focus();
      return;
    }
    setError(undefined);
    setNotice(null);
    setFound(undefined);
    search.mutate(parsed.criterion, {
      onSuccess: (foundPage) => {
        setFound(foundPage.items[0] ?? null);
      },
    });
  }

  function handleAssigned(message: string, result: AssignResult) {
    setAssign(null);
    setNotice(message);
    void queryClient.invalidateQueries({ queryKey: adminKeys.users });
    const patch = (current: AdminUser) =>
      result.kind === 'host'
        ? {
            ...current,
            hostProfileId: result.hostProfileId,
            roles: current.roles.includes('HOST') ? current.roles : [...current.roles, 'HOST'],
          }
        : {
            ...current,
            linkManagerId: result.linkManagerId,
            linkManagerStatus: 'ACTIVE' as const,
            roles: current.roles.includes('LINK_MANAGER')
              ? current.roles
              : [...current.roles, 'LINK_MANAGER'],
          };
    setFound((current) => (current ? patch(current) : current));
    setManagingUser((current) => (current ? patch(current) : current));
  }

  const activeUser =
    managingUser ??
    found ??
    (found === undefined && filteredItems.length === 1 ? filteredItems[0] : null);

  return (
    <div>
      <div className="mt-4 flex flex-wrap items-end justify-between gap-3">
        <nav aria-label="Filtrar usuarios por rol">
          <ul className="flex flex-wrap gap-2">
            {USER_ROLE_FILTERS.map((filter) => {
              const current = filter.value === activeRole;
              return (
                <li key={filter.value ?? 'TODOS'}>
                  <button
                    type="button"
                    aria-pressed={current}
                    onClick={() => {
                      setActiveRole(filter.value);
                    }}
                    className="inline-flex min-h-11 items-center rounded-full border-2 border-border bg-canvas px-4 font-semibold text-ink hover:bg-surface-subtle aria-pressed:border-brand aria-pressed:bg-brand aria-pressed:text-on-brand"
                  >
                    {filter.text}
                  </button>
                </li>
              );
            })}
          </ul>
        </nav>

        <form
          ref={formRef}
          noValidate
          onSubmit={handleSubmit}
          aria-label="Buscar a una persona"
          className="w-full sm:max-w-xs"
        >
          <label htmlFor={inputId} className="sr-only">
            Correo o número de documento
          </label>
          <div className={SEARCH_FORM_CLASS}>
            <input
              id={inputId}
              name="query"
              type="text"
              enterKeyHint="search"
              maxLength={100}
              autoComplete="off"
              autoCapitalize="none"
              spellCheck={false}
              aria-invalid={error ? 'true' : undefined}
              aria-describedby={error ? errorId : undefined}
              placeholder="Buscar"
              value={query}
              onChange={(event) => {
                setQuery(event.target.value);
                if (error) setError(undefined);
                if (found !== undefined) setFound(undefined);
              }}
              className={SEARCH_INPUT_CLASS}
            />
            <button
              type="submit"
              disabled={search.isPending}
              aria-label={search.isPending ? 'Buscando…' : 'Buscar'}
              className={SEARCH_BUTTON_CLASS}
            >
              <Icon name="search" size={22} />
              <span aria-hidden="true" className="hidden sm:inline">
                {search.isPending ? 'Buscando…' : 'Buscar'}
              </span>
            </button>
          </div>
        </form>
      </div>

      {error && (
        <p id={errorId} role="alert" className="mt-2 text-sm font-semibold text-danger-text">
          {error}
        </p>
      )}

      <div className="mt-4 grid gap-4" aria-live="polite">
        {search.isError && <ProblemNotice error={search.error} messages={RATE_LIMITED} />}
        {notice && <Notice tone="success">{notice}</Notice>}
      </div>

      <div className="mt-4">
        <TableFrame title="Usuarios" count={visibleCount}>
          <table className={TABLE_CLASS}>
            <caption className={CAPTION_CLASS}>
              Usuarios
              {visibleCount !== undefined && (
                <span className="font-normal text-ink-muted"> · {visibleCount}</span>
              )}
            </caption>
            <thead>
              <tr>
                <th scope="col" className={TH_CLASS}>
                  Persona
                </th>
                <th scope="col" className={TH_CLASS}>
                  Correo
                </th>
                <th scope="col" className={TH_CLASS}>
                  Roles vigentes
                </th>
                <th scope="col" className={TH_CLASS}>
                  Acciones
                </th>
              </tr>
            </thead>
            <tbody>
              {search.isPending && (
                <EmptyRow columns={4}>
                  <span role="status">Buscando usuario…</span>
                </EmptyRow>
              )}
              {!search.isPending && found === null && (
                <EmptyRow columns={4}>
                  No hay una cuenta con ese dato. Revisa que esté escrito completo; las cuentas de
                  anfitriones enrolados por un gestor no se encuentran por correo.
                </EmptyRow>
              )}
              {!search.isPending && found && !matchesRoleFilter && (
                <EmptyRow columns={4}>
                  La persona encontrada ({found.fullName}) no tiene el rol seleccionado en este
                  filtro.
                </EmptyRow>
              )}
              {!search.isPending && found && matchesRoleFilter && (
                <UserRow key={found.userId} user={found} onManage={setManagingUser} />
              )}
              {!search.isPending && found === undefined && (
                <>
                  {users.isPending && (
                    <EmptyRow columns={4}>
                      <span role="status">Cargando cuentas…</span>
                      <Skeleton className="mx-auto mt-3 h-6 w-2/3" />
                    </EmptyRow>
                  )}
                  {users.isSuccess && filteredItems.length === 0 && (
                    <EmptyRow columns={4}>
                      {query
                        ? 'No hay usuarios que coincidan con la búsqueda.'
                        : 'No hay usuarios registrados en este rol.'}
                    </EmptyRow>
                  )}
                  {filteredItems.map((user) => (
                    <UserRow key={user.userId} user={user} onManage={setManagingUser} />
                  ))}
                </>
              )}
            </tbody>
          </table>
        </TableFrame>

        {found === undefined && users.data && (
          <Pager
            label="usuarios"
            page={users.data.page}
            totalPages={users.data.totalPages}
            totalElements={users.data.totalElements}
            onChange={(next) => {
              const queryParams = new URLSearchParams(params);
              if (next > 0) queryParams.set('pagina', String(next + 1));
              else queryParams.delete('pagina');
              setParams(queryParams);
            }}
          />
        )}
      </div>

      {managingUser && (
        <ManageUserSheet
          key={managingUser.userId}
          user={managingUser}
          onClose={() => {
            setManagingUser(null);
          }}
          onUpdated={(updated, message) => {
            setManagingUser(null);
            setNotice(message);
            void queryClient.invalidateQueries({ queryKey: adminKeys.users });
            setFound((current) => (current?.userId === updated.userId ? updated : current));
          }}
          onAssign={(kind) => {
            setAssign(kind);
          }}
        />
      )}

      {activeUser && assign && (
        <AssignDialog
          key={assign}
          kind={assign}
          user={activeUser}
          onClose={() => {
            setAssign(null);
          }}
          onDone={handleAssigned}
        />
      )}
    </div>
  );
}
