import { useId, useState } from 'react';

import { ApiError } from '@/shared/api';
import { todayInBogota } from '@/shared/lib';
import { BottomSheet, Button, Icon, ProblemNotice, Select, TextField } from '@/shared/ui';

import type { AdminUser } from '../api';
import { accreditedOnProblem } from '../forms';
import { useAccreditLinkManager, useAssociationOptions, useCreateHostProfile } from '../queries';
import { Notice } from './parts';

export type AssignKind = 'host' | 'manager';

/** Qué se hizo, para que la pantalla actualice la cuenta encontrada sin repetir la búsqueda. */
export type AssignResult =
  | { readonly kind: 'host'; readonly hostProfileId: string }
  | { readonly kind: 'manager'; readonly linkManagerId: string };

interface AssignDialogProps {
  readonly kind: AssignKind;
  readonly user: AdminUser;
  readonly onClose: () => void;
  /** Se aplicó el alta; recibe el mensaje de resultado y lo que cambió. */
  readonly onDone: (message: string, result: AssignResult) => void;
}

const COPY = {
  host: {
    title: 'Convertir en anfitrión',
    consequence: 'queda como anfitrión de la asociación que elijas y podrá crear experiencias.',
    confirm: 'Convertir en anfitrión',
    done: 'ya es anfitrión.',
  },
  manager: {
    title: 'Acreditar como gestor de enlace',
    consequence:
      'queda acreditado como gestor de la asociación que elijas: podrá enrolar anfitriones y validar vouchers en campo.',
    confirm: 'Acreditar gestor',
    done: 'quedó acreditado como gestor de enlace.',
  },
} as const;

/** Mensajes de los 409 de negocio que `ProblemNotice` mostraría en genérico (ADR-0043 §1.6). */
const CONFLICTS: Readonly<Record<AssignKind, Record<string, string>>> = {
  host: {
    CATALOG_HOST_PROFILE_EXISTS:
      'No se pudo: esa persona ya es anfitrión, o la asociación elegida no está activa.',
  },
  manager: {
    ENROLLMENT_MANAGER_EXISTS: 'Esa persona ya tiene una acreditación de gestor vigente.',
    CATALOG_HOST_PROFILE_EXISTS: 'La asociación elegida no está activa. Elige otra.',
  },
};

/** Alta de anfitrión o de gestor: elegir la asociación (y la fecha, para el gestor) y confirmar. */
export function AssignDialog({ kind, user, onClose, onDone }: AssignDialogProps) {
  const copy = COPY[kind];
  const associations = useAssociationOptions();
  const createHost = useCreateHostProfile();
  const accredit = useAccreditLinkManager();
  const today = todayInBogota();
  const [associationId, setAssociationId] = useState('');
  const [accreditedOn, setAccreditedOn] = useState(today);
  const [associationError, setAssociationError] = useState<string | null>(null);
  const [dateError, setDateError] = useState<string | undefined>();
  const helpId = useId();

  const options = (associations.data?.items ?? [])
    .filter((association) => association.status === 'ACTIVE')
    .map((association) => ({ value: association.id, label: association.legalName }));
  const mutation = kind === 'host' ? createHost : accredit;
  const conflict =
    mutation.error instanceof ApiError && mutation.error.status === 409
      ? (CONFLICTS[kind][mutation.error.code ?? ''] ?? null)
      : null;
  const invalidDate =
    kind === 'manager' &&
    mutation.error instanceof ApiError &&
    mutation.error.status === 422 &&
    mutation.error.invalidParams.some((param) => param.name === 'accreditedOn');

  function submit() {
    const missing = associationId === '' ? 'Elige la asociación.' : null;
    const badDate = kind === 'manager' ? accreditedOnProblem(accreditedOn, today) : null;
    setAssociationError(missing);
    setDateError(badDate ?? undefined);
    if (missing || badDate) return;
    const done = `${user.fullName} ${copy.done}`;
    if (kind === 'host') {
      createHost.mutate(
        { userId: user.userId, associationId },
        {
          onSuccess: (created) => {
            onDone(done, { kind: 'host', hostProfileId: created.hostProfileId });
          },
        },
      );
    } else {
      accredit.mutate(
        { userId: user.userId, associationId, accreditedOn },
        {
          onSuccess: (created) => {
            onDone(done, { kind: 'manager', linkManagerId: created.linkManagerId });
          },
        },
      );
    }
  }

  return (
    <BottomSheet
      open
      onClose={onClose}
      title={copy.title}
      footer={
        <>
          <Button variant="secondary" className="flex-1" onClick={onClose}>
            Cancelar
          </Button>
          <Button className="flex-1" disabled={mutation.isPending} onClick={submit}>
            {mutation.isPending ? 'Enviando…' : copy.confirm}
          </Button>
        </>
      }
    >
      <div className="grid gap-5">
        <p className="text-ink">
          <span className="font-bold">{user.fullName}</span> {copy.consequence}
        </p>

        {associations.isError ? (
          <ProblemNotice
            error={associations.error}
            onRetry={() => {
              void associations.refetch();
            }}
          />
        ) : (
          <div>
            <Select
              label="Asociación"
              name="associationId"
              size="lg"
              searchable
              searchPlaceholder="Escribe para buscar la asociación…"
              value={associationId}
              options={options}
              disabled={associations.isPending}
              invalid={associationError !== null}
              describedBy={associationError ? `${helpId}-error` : undefined}
              onChange={(value) => {
                setAssociationId(value);
                setAssociationError(null);
              }}
            />
            {associationError && (
              <p
                id={`${helpId}-error`}
                className="mt-1 flex items-start gap-1.5 text-sm font-semibold text-danger-text"
              >
                <Icon name="alert" size={18} className="mt-px" />
                {associationError}
              </p>
            )}
          </div>
        )}

        {kind === 'manager' && (
          <TextField
            label="Fecha de acreditación"
            name="accreditedOn"
            type="date"
            max={today}
            value={accreditedOn}
            error={
              dateError ??
              (invalidDate ? 'La fecha de acreditación no puede ser futura.' : undefined)
            }
            onChange={(event) => {
              setAccreditedOn(event.target.value);
              setDateError(undefined);
            }}
          />
        )}

        <Notice tone="info" role="note">
          El rol se aplica cuando la persona vuelva a ingresar o se renueve su sesión.
        </Notice>

        {conflict && (
          <Notice tone="danger" role="alert">
            {conflict}
          </Notice>
        )}
        {mutation.isError && !conflict && !invalidDate && <ProblemNotice error={mutation.error} />}
      </div>
    </BottomSheet>
  );
}
