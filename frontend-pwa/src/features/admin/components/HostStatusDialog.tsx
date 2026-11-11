import { useId, useState } from 'react';

import { ApiError } from '@/shared/api';
import { BottomSheet, Button, Icon, ProblemNotice } from '@/shared/ui';

import type { ManagerAction } from '../api';
import { reasonProblem, REASON_MAX } from '../forms';
import { useChangeHostStatus } from '../queries';
import { Notice } from './parts';

interface ActionCopy {
  readonly title: string;
  readonly consequence: string;
  readonly confirm: string;
  readonly done: string;
}

const COPY: Readonly<Record<ManagerAction, ActionCopy>> = {
  suspend: {
    title: 'Suspender al anfitrión',
    consequence:
      'no podrá crear ni publicar experiencias ni gestionar franjas, y sus experiencias salen del catálogo. Puedes reactivarlo después.',
    confirm: 'Suspender',
    done: 'quedó suspendido.',
  },
  reinstate: {
    title: 'Reactivar al anfitrión',
    consequence: 'vuelve a operar y sus experiencias regresan al catálogo.',
    confirm: 'Reactivar',
    done: 'quedó reactivado.',
  },
  revoke: {
    title: 'Revocar al anfitrión',
    consequence:
      'pierde el rol de anfitrión y sus experiencias salen del catálogo. La revocación no se puede deshacer; su cuenta se conserva como turista.',
    confirm: 'Revocar definitivamente',
    done: 'dejó de ser anfitrión: su perfil fue revocado.',
  },
};

interface HostStatusDialogProps {
  readonly host: { readonly hostProfileId: string; readonly fullName: string };
  readonly action: ManagerAction;
  readonly onClose: () => void;
  /** El cambio se aplicó; recibe el mensaje de resultado. */
  readonly onDone: (message: string) => void;
}

/** Confirmación de suspender, reactivar o revocar a un anfitrión, con motivo obligatorio (ADR-0070 §2 y §5). */
export function HostStatusDialog({ host, action, onClose, onDone }: HostStatusDialogProps) {
  const copy = COPY[action];
  const change = useChangeHostStatus();
  const reasonId = useId();
  const [reason, setReason] = useState('');
  const [reasonError, setReasonError] = useState<string | null>(null);
  const stale = change.error instanceof ApiError && change.error.status === 409;

  function submit() {
    const problem = reasonProblem(reason);
    setReasonError(problem);
    if (problem) {
      document.getElementById(reasonId)?.focus();
      return;
    }
    change.mutate(
      { id: host.hostProfileId, action, reason: reason.trim() },
      {
        onSuccess: () => {
          onDone(`${host.fullName} ${copy.done}`);
        },
      },
    );
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
          <Button className="flex-1" disabled={change.isPending} onClick={submit}>
            {change.isPending ? 'Enviando…' : copy.confirm}
          </Button>
        </>
      }
    >
      <div className="grid gap-4">
        <p className="text-ink">
          <span className="font-bold">{host.fullName}</span> {copy.consequence}
        </p>

        <div>
          <label htmlFor={reasonId} className="block font-semibold text-ink">
            Motivo
          </label>
          <p id={`${reasonId}-ayuda`} className="mt-0.5 text-sm text-ink-muted">
            Queda en el historial de auditoría. No escribas datos personales.
          </p>
          <textarea
            id={reasonId}
            name="reason"
            rows={4}
            maxLength={REASON_MAX}
            value={reason}
            aria-invalid={reasonError ? true : undefined}
            aria-describedby={`${reasonId}-ayuda${reasonError ? ` ${reasonId}-error` : ''}`}
            onChange={(event) => {
              setReason(event.target.value);
            }}
            className={`mt-1.5 w-full rounded-lg border-2 bg-canvas px-3 py-2 text-base text-ink ${
              reasonError ? 'border-danger-border' : 'border-border'
            }`}
          />
          {reasonError && (
            <p
              id={`${reasonId}-error`}
              className="mt-1 flex items-start gap-1.5 text-sm font-semibold text-danger-text"
            >
              <Icon name="alert" size={18} className="mt-px" />
              {reasonError}
            </p>
          )}
        </div>

        {action !== 'reinstate' && (
          <Notice tone="warning" role="note">
            Las reservas ya confirmadas de este anfitrión no se cancelan solas: hay que resolverlas
            aparte.
          </Notice>
        )}
        {action === 'revoke' && (
          <Notice tone="warning" role="note">
            Si tiene una sesión abierta, su token puede seguir válido hasta 15 minutos.
          </Notice>
        )}
        {stale && (
          <Notice tone="danger" role="alert">
            El perfil cambió de estado mientras lo revisabas. Ya actualizamos la lista: cierra este
            aviso y revisa su estado actual.
          </Notice>
        )}
        {change.isError && !stale && <ProblemNotice error={change.error} />}
      </div>
    </BottomSheet>
  );
}
