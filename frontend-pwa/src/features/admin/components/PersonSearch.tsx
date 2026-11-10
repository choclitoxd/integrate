import { useRef, useState, type SubmitEvent } from 'react';

import { DOC_TYPES } from '@/features/auth';
import { Button, Icon, ProblemNotice, Select, TextField } from '@/shared/ui';

import type { AdminUser, UserCriterion } from '../api';
import { documentSearchProblem, emailSearchProblem } from '../forms';
import { formatInstant, MANAGER_STATUS, roleName } from '../labels';
import { useSearchUser, useUserDetail } from '../queries';
import { AssignDialog, type AssignKind, type AssignResult } from './AssignDialog';
import { Notice, StatusBadge } from './parts';

type Mode = 'email' | 'document';

const RATE_LIMITED = {
  ADMIN_LOOKUP_RATE_LIMITED:
    'Hiciste demasiadas búsquedas seguidas (el límite es 20 por minuto). Espera un momento e inténtalo de nuevo.',
};

const DOC_OPTIONS = DOC_TYPES.map((type) => ({ value: type.value, label: type.label }));

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

function Result({ user, onAssign }: { user: AdminUser; onAssign: (kind: AssignKind) => void }) {
  const [showHistory, setShowHistory] = useState(false);
  return (
    <section
      aria-label="Persona encontrada"
      className="rounded-xl border-2 border-border-strong bg-canvas px-5 py-5"
    >
      <h3 className="text-xl font-bold text-ink">{user.fullName}</h3>
      <p className="mt-1 break-all text-ink">
        {user.email}
        {user.emailMasked && (
          <span className="text-ink-muted"> (parcialmente oculto: la buscaste por documento)</span>
        )}
      </p>
      <ul aria-label="Roles vigentes" className="mt-3 flex flex-wrap gap-2">
        {user.roles.map((role) => (
          <li key={role}>
            <StatusBadge tone="info">{roleName(role)}</StatusBadge>
          </li>
        ))}
      </ul>

      <div className="mt-5 grid gap-4 border-t border-border-subtle pt-5 sm:grid-cols-2">
        <div className="grid content-start gap-2">
          {user.hostProfileId ? (
            <p className="font-semibold text-ink">Ya es anfitrión.</p>
          ) : (
            <Button
              onClick={() => {
                onAssign('host');
              }}
            >
              <Icon name="plus" />
              Convertir en anfitrión
            </Button>
          )}
        </div>
        <div className="grid content-start gap-2">
          {user.linkManagerId && user.linkManagerStatus ? (
            <p className="font-semibold text-ink">
              Ya tiene acreditación de gestor ({MANAGER_STATUS[user.linkManagerStatus].text}).
            </p>
          ) : (
            <Button
              variant="secondary"
              onClick={() => {
                onAssign('manager');
              }}
            >
              <Icon name="route" />
              Acreditar como gestor
            </Button>
          )}
        </div>
      </div>

      <div className="mt-4">
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
      </div>
    </section>
  );
}

/**
 * Búsqueda exacta de una persona por correo o por documento (ADR-0043 §1.1) y las altas que parten
 * de ella. El resultado vive solo en esta pantalla: no se guarda en caché ni se repite solo.
 */
export function PersonSearch() {
  const search = useSearchUser();
  const formRef = useRef<HTMLFormElement>(null);
  const [mode, setMode] = useState<Mode>('email');
  const [email, setEmail] = useState('');
  const [docType, setDocType] = useState('CC');
  const [document, setDocument] = useState('');
  const [error, setError] = useState<string | undefined>();
  const [found, setFound] = useState<AdminUser | null | undefined>(undefined);
  const [assign, setAssign] = useState<AssignKind | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  function criterion(): UserCriterion {
    return mode === 'email'
      ? { kind: 'email', email: email.trim() }
      : {
          kind: 'document',
          docType: docType as Extract<UserCriterion, { kind: 'document' }>['docType'],
          document: document.trim(),
        };
  }

  function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    const problem = mode === 'email' ? emailSearchProblem(email) : documentSearchProblem(document);
    setError(problem ?? undefined);
    if (problem) {
      (
        formRef.current?.elements.namedItem(
          mode === 'email' ? 'email' : 'document',
        ) as HTMLElement | null
      )?.focus();
      return;
    }
    setNotice(null);
    setFound(undefined);
    search.mutate(criterion(), {
      onSuccess: (page) => {
        setFound(page.items[0] ?? null);
      },
    });
  }

  function handleAssigned(message: string, result: AssignResult) {
    setAssign(null);
    setNotice(message);
    setFound((current) =>
      current
        ? result.kind === 'host'
          ? { ...current, hostProfileId: result.hostProfileId }
          : { ...current, linkManagerId: result.linkManagerId, linkManagerStatus: 'ACTIVE' }
        : current,
    );
  }

  return (
    <section
      aria-labelledby="buscar-persona-titulo"
      className="rounded-xl border-2 border-border-subtle bg-canvas px-5 py-5"
    >
      <h2 id="buscar-persona-titulo" className="text-xl font-bold text-ink">
        Buscar a una persona
      </h2>
      <p className="mt-1 text-ink-muted">
        Escribe el correo o el documento <span className="font-semibold">completo</span>: no hay
        búsqueda parcial. Cada búsqueda queda registrada en la bitácora.
      </p>

      <form
        ref={formRef}
        noValidate
        onSubmit={handleSubmit}
        aria-label="Buscar a una persona"
        className="mt-4 grid gap-4"
      >
        <fieldset>
          <legend className="font-semibold text-ink">Buscar por</legend>
          <div className="mt-1.5 flex flex-wrap gap-2">
            {(
              [
                ['email', 'Correo'],
                ['document', 'Documento'],
              ] as const
            ).map(([value, label]) => (
              <label
                key={value}
                className="flex min-h-11 cursor-pointer items-center justify-center rounded-lg border-2 border-border bg-canvas px-4 font-semibold text-ink has-checked:border-brand has-checked:bg-brand has-checked:text-on-brand"
              >
                <input
                  type="radio"
                  name="criterio"
                  value={value}
                  checked={mode === value}
                  onChange={() => {
                    setMode(value);
                    setError(undefined);
                  }}
                  className="sr-only"
                />
                {label}
              </label>
            ))}
          </div>
        </fieldset>

        {mode === 'email' ? (
          <TextField
            label="Correo exacto"
            name="email"
            type="email"
            inputMode="email"
            autoComplete="off"
            autoCapitalize="none"
            spellCheck={false}
            value={email}
            error={error}
            onChange={(event) => {
              setEmail(event.target.value);
            }}
          />
        ) : (
          <div className="grid items-start gap-4 sm:grid-cols-[16rem_1fr]">
            <Select
              label="Tipo de documento"
              name="docType"
              size="lg"
              value={docType}
              options={DOC_OPTIONS}
              onChange={setDocType}
            />
            <TextField
              label="Número de documento"
              name="document"
              inputMode="text"
              autoComplete="off"
              spellCheck={false}
              value={document}
              error={error}
              onChange={(event) => {
                setDocument(event.target.value);
              }}
            />
          </div>
        )}

        <div>
          <Button type="submit" disabled={search.isPending}>
            <Icon name="search" />
            {search.isPending ? 'Buscando…' : 'Buscar'}
          </Button>
        </div>
      </form>

      <div className="mt-5 grid gap-4" aria-live="polite">
        {search.isError && <ProblemNotice error={search.error} messages={RATE_LIMITED} />}
        {notice && <Notice tone="success">{notice}</Notice>}
        {found === null && (
          <p className="rounded-xl bg-surface px-4 py-4 text-ink">
            No hay una cuenta con ese dato. Revisa que esté escrito completo; las cuentas de
            anfitriones enrolados por un gestor no se encuentran por correo.
          </p>
        )}
        {found && <Result user={found} onAssign={setAssign} />}
      </div>

      {found && assign && (
        <AssignDialog
          key={assign}
          kind={assign}
          user={found}
          onClose={() => {
            setAssign(null);
          }}
          onDone={handleAssigned}
        />
      )}
    </section>
  );
}
