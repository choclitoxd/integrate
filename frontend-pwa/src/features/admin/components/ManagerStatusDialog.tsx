import { ApiError } from '@/shared/api';
import { BottomSheet, Button, ProblemNotice } from '@/shared/ui';

import type { ManagerAction } from '../api';
import { useChangeManagerStatus } from '../queries';
import { Notice } from './parts';

interface ActionCopy {
  readonly title: string;
  readonly consequence: string;
  readonly confirm: string;
  readonly done: string;
}

const COPY: Readonly<Record<ManagerAction, ActionCopy>> = {
  suspend: {
    title: 'Suspender al gestor',
    consequence:
      'no podrá enrolar anfitriones ni descargar el padrón mientras esté suspendido. Puedes reactivarlo después.',
    confirm: 'Suspender',
    done: 'quedó suspendido.',
  },
  reinstate: {
    title: 'Reactivar al gestor',
    consequence: 'vuelve a poder enrolar anfitriones y operar en campo.',
    confirm: 'Reactivar',
    done: 'quedó reactivado.',
  },
  revoke: {
    title: 'Revocar la acreditación',
    consequence:
      'pierde el rol de gestor de enlace. La revocación no se puede deshacer: tendría que acreditarse de nuevo.',
    confirm: 'Revocar definitivamente',
    done: 'dejó de ser gestor: su acreditación fue revocada.',
  },
};

interface ManagerStatusDialogProps {
  readonly manager: { readonly linkManagerId: string; readonly fullName: string };
  readonly action: ManagerAction;
  readonly onClose: () => void;
  /** El cambio se aplicó; recibe el mensaje de resultado. */
  readonly onDone: (message: string, action: ManagerAction) => void;
}

/** Confirmación de suspender, reactivar o revocar: repite a quién afecta y qué pasa (ADR-0042 §5). */
export function ManagerStatusDialog({
  manager,
  action,
  onClose,
  onDone,
}: ManagerStatusDialogProps) {
  const copy = COPY[action];
  const change = useChangeManagerStatus();
  const stale = change.error instanceof ApiError && change.error.status === 409;

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
          <Button
            className="flex-1"
            disabled={change.isPending}
            onClick={() => {
              change.mutate(
                { id: manager.linkManagerId, action },
                {
                  onSuccess: () => {
                    onDone(`${manager.fullName} ${copy.done}`, action);
                  },
                },
              );
            }}
          >
            {change.isPending ? 'Enviando…' : copy.confirm}
          </Button>
        </>
      }
    >
      <div className="grid gap-4">
        <p className="text-ink">
          <span className="font-bold">{manager.fullName}</span> {copy.consequence}
        </p>
        {action === 'revoke' && (
          <Notice tone="warning" role="note">
            Si tiene una sesión abierta, su token puede seguir válido hasta 15 minutos.
          </Notice>
        )}
        {stale && (
          <Notice tone="danger" role="alert">
            La acreditación cambió de estado mientras la revisabas. Ya actualizamos la lista: cierra
            este aviso y revisa su estado actual.
          </Notice>
        )}
        {change.isError && !stale && <ProblemNotice error={change.error} />}
      </div>
    </BottomSheet>
  );
}
