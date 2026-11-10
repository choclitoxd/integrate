import { useId, useRef, useState, type SubmitEvent } from 'react';
import { useSearchParams } from 'react-router';
import { useQueryClient } from '@tanstack/react-query';

import { Button, Icon, ProblemNotice, Skeleton } from '@/shared/ui';

import type { AdminUser, UserRole } from '../api';
import { parsePersonSearchQuery } from '../forms';
import { formatInstant, MANAGER_STATUS, roleName } from '../labels';
import { adminKeys, useSearchUser, useUserDetail, useUsers } from '../queries';
import { AssignDialog, type AssignKind, type AssignResult } from './AssignDialog';
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

/** Historial de roles de la cuenta, pedido solo cuando se abre (ADR-0043 §1.5). */
function RoleHistory({ userId }: { userId: string }) {
  const detail = useUserDetail(userId);
  if (detail.isPending) return <p className="mt-2 text-ink-muted">Cargando historial…</p>;
  if (detail.isError) {
    return (
      <div className="mt-2">
        <ProblemNotice
          error={detail.error}
          onRetry={() => {
            void detail.refetch();
          }}
        />
      </div>
    );
  }
  return (
    <div className="mt-2 grid gap-1 text-ink">
      <p className="text-sm text-ink-muted">
        Documento {detail.data.docType} terminado en {detail.data.docNumberLast4}
      </p>
      <ul aria-label="Historial de roles" className="grid gap-1">
        {detail.data.roleHistory.map((entry, index) => (
          <li key={`${entry.role}-${String(index)}`}>
            <span className="font-semibold">{roleName(entry.role)}</span>
            {entry.revokedAt ? (
              <span className="text-ink-muted">
                {' '}
                · revocado el {formatInstant(entry.revokedAt)}
              </span>
            ) : (
              <span className="text-ink-muted"> · vigente</span>
            )}
          </li>
        ))}
      </ul>
    </div>
  );
}

function UserRow({ user, onAssign }: { user: AdminUser; onAssign: (kind: AssignKind) => void }) {
  const [showHistory, setShowHistory] = useState(false);
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
          variant="quiet"
          aria-expanded={showHistory}
          onClick={() => {
            setShowHistory((open) => !open);
          }}
        >
          {showHistory ? 'Ocultar historial de roles' : 'Ver historial de roles'}
        </Button>
        {showHistory && <RoleHistory userId={user.userId} />}
      </td>
      <td className={TD_CLASS}>
        <div className="flex flex-wrap items-center gap-2">
          {user.hostProfileId ? (
            <span className="text-sm font-semibold text-ink">Ya es anfitrión.</span>
          ) : (
            <Button
              onClick={() => {
                onAssign('host');
              }}
            >
              <Icon name="plus" />
              <span className="hidden sm:inline">Convertir en anfitrión</span>
            </Button>
          )}
          {user.linkManagerId && user.linkManagerStatus ? (
            <span className="text-sm font-semibold text-ink">
              Ya tiene acreditación de gestor ({MANAGER_STATUS[user.linkManagerStatus].text}).
            </span>
          ) : (
            <Button
              variant="secondary"
              onClick={() => {
                onAssign('manager');
              }}
            >
              <Icon name="route" />
              <span className="hidden sm:inline">Acreditar como gestor</span>
            </Button>
          )}
        </div>
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
    setFound((current) =>
      current
        ? result.kind === 'host'
          ? {
              ...current,
              hostProfileId: result.hostProfileId,
              roles: current.roles.includes('HOST') ? current.roles : [...current.roles, 'HOST'],
            }
          : {
              ...current,
              linkManagerId: result.linkManagerId,
              linkManagerStatus: 'ACTIVE',
              roles: current.roles.includes('LINK_MANAGER')
                ? current.roles
                : [...current.roles, 'LINK_MANAGER'],
            }
        : current,
    );
  }

  const activeUser =
    found ?? (found === undefined && filteredItems.length === 1 ? filteredItems[0] : null);

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
                  Historial
                </th>
                <th scope="col" className={TH_CLASS}>
                  Acciones
                </th>
              </tr>
            </thead>
            <tbody>
              {search.isPending && (
                <EmptyRow columns={5}>
                  <span role="status">Buscando usuario…</span>
                </EmptyRow>
              )}
              {!search.isPending && found === null && (
                <EmptyRow columns={5}>
                  No hay una cuenta con ese dato. Revisa que esté escrito completo; las cuentas de
                  anfitriones enrolados por un gestor no se encuentran por correo.
                </EmptyRow>
              )}
              {!search.isPending && found && !matchesRoleFilter && (
                <EmptyRow columns={5}>
                  La persona encontrada ({found.fullName}) no tiene el rol seleccionado en este
                  filtro.
                </EmptyRow>
              )}
              {!search.isPending && found && matchesRoleFilter && (
                <UserRow key={found.userId} user={found} onAssign={setAssign} />
              )}
              {!search.isPending && found === undefined && (
                <>
                  {users.isPending && (
                    <EmptyRow columns={5}>
                      <span role="status">Cargando cuentas…</span>
                      <Skeleton className="mx-auto mt-3 h-6 w-2/3" />
                    </EmptyRow>
                  )}
                  {users.isSuccess && filteredItems.length === 0 && (
                    <EmptyRow columns={5}>
                      {query
                        ? 'No hay usuarios que coincidan con la búsqueda.'
                        : 'No hay usuarios registrados en este rol.'}
                    </EmptyRow>
                  )}
                  {filteredItems.map((user) => (
                    <UserRow key={user.userId} user={user} onAssign={setAssign} />
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
