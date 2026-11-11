import { useId, useState } from 'react';

import { ApiError } from '@/shared/api';
import { BottomSheet, Button, Icon, ProblemNotice } from '@/shared/ui';

import type { AdminExperience, ModerationAction } from '../api';
import { reasonProblem, REASON_MAX } from '../forms';
import { useModerateExperience } from '../queries';
import { Notice } from './parts';

interface ActionCopy {
  readonly title: string;
  /** Qué pasa al confirmar, dicho antes de hacerlo. */
  readonly consequence: string;
  readonly confirm: string;
  readonly needsReason: boolean;
  readonly done: string;
}

const COPY: Readonly<Record<ModerationAction, ActionCopy>> = {
  approve: {
    title: 'Aprobar y publicar',
    consequence: 'La experiencia aparecerá en el catálogo público y se podrá reservar.',
    confirm: 'Aprobar y publicar',
    needsReason: false,
    done: 'Experiencia aprobada: ya está en el catálogo.',
  },
  reject: {
    title: 'Rechazar',
    consequence: 'Vuelve al anfitrión con tu motivo para que la corrija y la envíe de nuevo.',
    confirm: 'Rechazar',
    needsReason: true,
    done: 'Experiencia rechazada: el anfitrión verá el motivo.',
  },
  suspend: {
    title: 'Suspender',
    consequence: 'Sale del catálogo y sus fechas futuras dejan de poder reservarse.',
    confirm: 'Suspender',
    needsReason: true,
    done: 'Experiencia suspendida: ya no aparece en el catálogo.',
  },
  reinstate: {
    title: 'Reactivar',
    consequence: 'Vuelve al catálogo público con sus fechas abiertas.',
    confirm: 'Reactivar',
    needsReason: false,
    done: 'Experiencia reactivada: volvió al catálogo.',
  },
  retire: {
    title: 'Retirar definitivamente',
    consequence: 'El retiro no se puede deshacer: la experiencia no vuelve a publicarse.',
    confirm: 'Retirar definitivamente',
    needsReason: true,
    done: 'Experiencia retirada.',
  },
  'confirm-meeting-point': {
    title: 'Confirmar el punto de encuentro',
    consequence:
      'Aceptas las coordenadas del anfitrión aunque no coincidan con la dirección. Queda en el historial.',
    confirm: 'Confirmar punto',
    needsReason: false,
    done: 'Punto de encuentro confirmado: ya puedes aprobar la experiencia.',
  },
};

/** Errores de negocio de la moderación, en español (ADR-0042 §5). */
function conflictMessage(error: unknown): string | null {
  if (!(error instanceof ApiError) || error.status !== 409) return null;
  if (error.code === 'CATALOG_MEETING_POINT_MISMATCH') {
    return 'El punto de encuentro no coincide con la dirección. Cierra este aviso y usa «Confirmar el punto de encuentro» en la ficha, o rechaza la experiencia.';
  }
  return 'La experiencia cambió de estado mientras la revisabas. Ya actualizamos la lista: cierra este aviso y revisa su estado actual.';
}

interface ModerationDialogProps {
  readonly experience: AdminExperience;
  readonly action: ModerationAction;
  readonly onClose: () => void;
  /** La acción se aplicó; recibe el mensaje de resultado. */
  readonly onDone: (message: string) => void;
}

/** Confirmación de una acción de moderación: repite a qué experiencia afecta y pide el motivo. */
export function ModerationDialog({ experience, action, onClose, onDone }: ModerationDialogProps) {
  const copy = COPY[action];
  const moderate = useModerateExperience();
  const [reason, setReason] = useState('');
  const [reasonError, setReasonError] = useState<string | null>(null);
  const reasonId = useId();
  const conflict = conflictMessage(moderate.error);

  function submit() {
    if (copy.needsReason) {
      const problem = reasonProblem(reason);
      setReasonError(problem);
      if (problem) {
        document.getElementById(reasonId)?.focus();
        return;
      }
    }
    moderate.mutate(
      {
        id: experience.id,
        action,
        ...(copy.needsReason && { reason: reason.trim() }),
      },
      {
        onSuccess: () => {
          onDone(copy.done);
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
          <Button className="flex-1" disabled={moderate.isPending} onClick={submit}>
            {moderate.isPending ? 'Enviando…' : copy.confirm}
          </Button>
        </>
      }
    >
      <div className="grid gap-4">
        <p className="text-ink">
          <span className="font-bold">«{experience.title}»</span>
          <br />
          {copy.consequence}
        </p>

        {copy.needsReason && (
          <div>
            <label htmlFor={reasonId} className="block font-semibold text-ink">
              Motivo
            </label>
            <p id={`${reasonId}-ayuda`} className="mt-0.5 text-sm text-ink-muted">
              Lo lee el anfitrión y queda en el historial. No escribas datos personales.
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
        )}

        {conflict && (
          <Notice tone="danger" role="alert">
            {conflict}
          </Notice>
        )}
        {moderate.isError && !conflict && <ProblemNotice error={moderate.error} />}
      </div>
    </BottomSheet>
  );
}
