import { useRef, useState, type SubmitEvent } from 'react';

import { DOC_TYPES, NUMERIC_DOC_TYPES, type DocType, type FieldErrors } from '@/features/auth';
import { ApiError } from '@/shared/api';
import {
  BottomSheet,
  Button,
  Icon,
  PhoneField,
  ProblemNotice,
  Select,
  TextField,
} from '@/shared/ui';

import type { AdminUser } from '../api';
import {
  USER_EDIT_FIELDS,
  userEditServerErrors,
  validateUserEditDraft,
  type UserEditDraft,
  type UserEditField,
} from '../forms';
import { formatInstant, MANAGER_STATUS, roleName } from '../labels';
import { useUpdateUser, useUserDetail } from '../queries';
import type { AssignKind } from './AssignDialog';
import { StatusBadge } from './parts';

interface ManageUserSheetProps {
  readonly user: AdminUser;
  readonly onClose: () => void;
  readonly onUpdated: (updated: AdminUser, message: string) => void;
  readonly onAssign: (kind: AssignKind) => void;
}

/**
 * Gestión integral de una cuenta por el administrador (ADR-0057 §2):
 * Ficha editable con guardado contra `PUT /api/v1/admin/users/{id}`,
 * historial de roles consultado bajo demanda y acciones contextuales.
 */
export function ManageUserSheet({ user, onClose, onUpdated, onAssign }: ManageUserSheetProps) {
  const detail = useUserDetail(user.userId);
  const updateUser = useUpdateUser();
  const formRef = useRef<HTMLFormElement>(null);

  const [activeTab, setActiveTab] = useState<'edit' | 'roles'>('edit');
  const [draft, setDraft] = useState<UserEditDraft>(() => ({
    fullName: user.fullName,
    docType: 'CC',
    docNumber: '',
    phone: '+57 ',
  }));
  const [errors, setErrors] = useState<FieldErrors<UserEditField>>({});
  const [submitError, setSubmitError] = useState<string | null>(null);

  function setField<K extends UserEditField>(key: K, value: UserEditDraft[K]) {
    setDraft((prev) => ({ ...prev, [key]: value }));
    if (errors[key]) {
      setErrors((prev) => Object.fromEntries(Object.entries(prev).filter(([k]) => k !== key)));
    }
    if (submitError) setSubmitError(null);
  }

  function focusFirst(fields: FieldErrors<UserEditField>) {
    const first = USER_EDIT_FIELDS.find((name) => Boolean(fields[name]));
    if (first) {
      (formRef.current?.elements.namedItem(first) as HTMLElement | null)?.focus();
    }
  }

  function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    const result = validateUserEditDraft(draft);
    if (!result.ok) {
      setErrors(result.errors);
      focusFirst(result.errors);
      return;
    }

    updateUser.mutate(
      { id: user.userId, body: result.body },
      {
        onSuccess: (updated) => {
          onUpdated(updated, `Se actualizaron los datos de «${updated.fullName}».`);
        },
        onError: (error) => {
          if (error instanceof ApiError && error.status === 409) {
            const next = { docNumber: 'Ya existe otra persona registrada con este documento.' };
            setErrors(next);
            focusFirst(next);
          } else if (error instanceof ApiError && error.invalidParams.length > 0) {
            const mapped = userEditServerErrors(error.invalidParams);
            setErrors(mapped);
            focusFirst(mapped);
          } else if (error instanceof ApiError) {
            setSubmitError(error.problem.title);
          }
        },
      },
    );
  }

  const docNumeric = NUMERIC_DOC_TYPES.includes(draft.docType);

  return (
    <BottomSheet open onClose={onClose} title={`Gestionar · ${user.fullName}`}>
      <div className="grid gap-4">
        <div
          role="tablist"
          aria-label="Secciones de gestión de persona"
          className="flex border-b border-border pb-2 gap-2"
        >
          <button
            type="button"
            role="tab"
            aria-selected={activeTab === 'edit'}
            onClick={() => {
              setActiveTab('edit');
            }}
            className="rounded-lg px-3 py-2 text-sm font-semibold transition-colors aria-selected:bg-brand-soft aria-selected:text-brand text-ink hover:bg-surface-subtle"
          >
            Ficha y edición
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={activeTab === 'roles'}
            onClick={() => {
              setActiveTab('roles');
            }}
            className="rounded-lg px-3 py-2 text-sm font-semibold transition-colors aria-selected:bg-brand-soft aria-selected:text-brand text-ink hover:bg-surface-subtle"
          >
            Roles e historial
          </button>
        </div>

        {activeTab === 'edit' && (
          <form ref={formRef} noValidate onSubmit={handleSubmit} className="grid gap-4">
            {submitError && (
              <p role="alert" className="text-sm font-semibold text-danger-text">
                {submitError}
              </p>
            )}

            <div className="rounded-lg border border-border-subtle bg-surface-subtle p-3 text-sm text-ink-muted">
              Correo de cuenta:{' '}
              <span className="font-semibold text-ink break-all">{user.email}</span>{' '}
              <span className="text-xs text-ink-muted">(inmutable por seguridad)</span>
            </div>

            <TextField
              label="Nombre completo"
              name="fullName"
              value={draft.fullName}
              error={errors.fullName}
              required
              maxLength={150}
              placeholder="Ej. Laura Gómez"
              onChange={(event) => {
                setField('fullName', event.target.value);
              }}
            />

            <div className="grid gap-3 sm:grid-cols-[minmax(0,12rem)_minmax(0,1fr)]">
              <Select
                label="Tipo de documento"
                name="docType"
                value={draft.docType}
                options={DOC_TYPES}
                onChange={(value) => {
                  setField('docType', value as DocType);
                }}
              />
              <TextField
                label="Número de documento"
                name="docNumber"
                value={draft.docNumber}
                error={errors.docNumber}
                required
                maxLength={20}
                placeholder={
                  detail.data?.docNumberLast4
                    ? `Actual termina en ${detail.data.docNumberLast4}`
                    : 'Ej. 1098765432'
                }
                inputMode={docNumeric ? 'numeric' : 'text'}
                pattern={docNumeric ? '[0-9]*' : undefined}
                onChange={(event) => {
                  setField('docNumber', event.target.value);
                }}
              />
            </div>

            <PhoneField
              label="Celular de contacto"
              name="phone"
              value={draft.phone}
              error={errors.phone}
              onChange={(value) => {
                setField('phone', value);
              }}
            />

            <div className="mt-2 flex flex-wrap justify-end gap-3">
              <Button variant="secondary" onClick={onClose}>
                <Icon name="close" />
                Cancelar
              </Button>
              <Button type="submit" disabled={updateUser.isPending}>
                <Icon name="check" />
                {updateUser.isPending ? 'Guardando…' : 'Guardar cambios'}
              </Button>
            </div>
          </form>
        )}

        {activeTab === 'roles' && (
          <div className="grid gap-4">
            <div>
              <p className="text-sm font-semibold text-ink mb-2">Roles vigentes</p>
              <ul aria-label="Roles vigentes" className="flex flex-wrap gap-2">
                {user.roles.map((role) => (
                  <li key={role}>
                    <StatusBadge tone="info">{roleName(role)}</StatusBadge>
                  </li>
                ))}
              </ul>
            </div>

            <div className="border-t border-border pt-3">
              <p className="text-sm font-semibold text-ink mb-2">Asignaciones de rol</p>
              <div className="flex flex-wrap items-center gap-3">
                {user.hostProfileId ? (
                  <span className="text-sm font-semibold text-ink">
                    Ya cuenta con perfil de anfitrión.
                  </span>
                ) : (
                  <Button
                    onClick={() => {
                      onClose();
                      onAssign('host');
                    }}
                  >
                    <Icon name="plus" />
                    Convertir en anfitrión
                  </Button>
                )}

                {user.linkManagerId && user.linkManagerStatus ? (
                  <span className="text-sm font-semibold text-ink">
                    Acreditación de gestor ({MANAGER_STATUS[user.linkManagerStatus].text}).
                  </span>
                ) : (
                  <Button
                    variant="secondary"
                    onClick={() => {
                      onClose();
                      onAssign('manager');
                    }}
                  >
                    <Icon name="route" />
                    Acreditar como gestor
                  </Button>
                )}
              </div>
            </div>

            <div className="border-t border-border pt-3">
              <p className="text-sm font-semibold text-ink mb-2">Historial de roles</p>
              {detail.isPending && <p className="text-sm text-ink-muted">Cargando historial…</p>}
              {detail.isError && <ProblemNotice error={detail.error} />}
              {detail.isSuccess && (
                <div className="grid gap-2 text-ink">
                  <p className="text-sm text-ink-muted">
                    Documento {detail.data.docType} terminado en {detail.data.docNumberLast4}
                  </p>
                  <ul aria-label="Historial de roles" className="grid gap-1">
                    {detail.data.roleHistory.map((entry, index) => (
                      <li key={`${entry.role}-${String(index)}`} className="text-sm">
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
              )}
            </div>

            <div className="mt-2 flex justify-end">
              <Button variant="secondary" onClick={onClose}>
                <Icon name="close" />
                Cerrar
              </Button>
            </div>
          </div>
        )}
      </div>
    </BottomSheet>
  );
}
