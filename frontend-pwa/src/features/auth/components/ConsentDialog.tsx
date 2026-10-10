import { useId, useState, type SubmitEvent } from 'react';

import type { ConsentPurpose } from '@/shared/session';
import { BottomSheet, Button, ProblemNotice } from '@/shared/ui';

import { useCurrentPolicy, useGrantConsents } from '../queries';
import { ConsentCheckbox } from './ConsentCheckbox';
import { PolicyLink } from './PolicyLink';

interface ConsentDialogProps {
  readonly open: boolean;
  readonly purposes: readonly ConsentPurpose[];
  /** Versión a aceptar; si falta, la vigente del endpoint público. */
  readonly policyVersion?: string | undefined;
  /**
   * `required`: re-consentimiento bloqueante; "No acepto" llama a `onDecline`.
   * `optional`: se puede posponer con "Ahora no" (p. ej. HEALTH_EMERGENCY antes de reservar).
   */
  readonly mode: 'required' | 'optional';
  readonly onGranted: () => void;
  readonly onDecline: () => void;
}

/** Diálogo de consentimiento Habeas Data reutilizable (ADR-0013 §5). */
export function ConsentDialog({
  open,
  purposes,
  policyVersion,
  mode,
  onGranted,
  onDecline,
}: ConsentDialogProps) {
  const formId = useId();
  const policy = useCurrentPolicy(open);
  const grant = useGrantConsents();
  const [checked, setChecked] = useState<ReadonlySet<ConsentPurpose>>(new Set());
  const [showErrors, setShowErrors] = useState(false);
  const [wasOpen, setWasOpen] = useState(open);
  if (open !== wasOpen) {
    setWasOpen(open);
    if (open) {
      setChecked(new Set());
      setShowErrors(false);
    }
  }

  const version = policyVersion ?? policy.data?.version;
  const allChecked = purposes.every((purpose) => checked.has(purpose));

  function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!allChecked || !version) {
      setShowErrors(true);
      return;
    }
    grant.mutate({ policyVersion: version, purposes }, { onSuccess: onGranted });
  }

  const required = mode === 'required';

  return (
    <BottomSheet
      open={open}
      onClose={onDecline}
      dismissible={!required}
      title={required ? 'Actualizamos nuestra política de datos' : 'Autorización necesaria'}
      footer={
        <>
          <Button variant="secondary" className="flex-1" onClick={onDecline}>
            {required ? 'No acepto' : 'Ahora no'}
          </Button>
          <Button type="submit" form={formId} className="flex-[2]" disabled={grant.isPending}>
            {grant.isPending ? 'Guardando…' : 'Acepto'}
          </Button>
        </>
      }
    >
      <form id={formId} onSubmit={handleSubmit} className="grid gap-4" noValidate>
        <p className="text-ink">
          {required
            ? 'Para seguir usando Intégrate necesitamos tu autorización sobre la versión vigente de la política.'
            : 'Para continuar necesitamos esta autorización. Puedes revisarla en tu cuenta cuando quieras.'}
        </p>
        {policy.data && <PolicyLink policy={policy.data} />}
        {purposes.map((purpose) => (
          <ConsentCheckbox
            key={purpose}
            purpose={purpose}
            required
            checked={checked.has(purpose)}
            onChange={(value) => {
              const next = new Set(checked);
              if (value) next.add(purpose);
              else next.delete(purpose);
              setChecked(next);
            }}
            error={
              showErrors && !checked.has(purpose) ? 'Marca la casilla para continuar.' : undefined
            }
          />
        ))}
        {required && (
          <p className="text-sm text-ink-muted">
            Si no aceptas, cerraremos tu sesión. Tus datos no se modifican.
          </p>
        )}
        {grant.isError && <ProblemNotice error={grant.error} />}
        {policy.isError && !version && <ProblemNotice error={policy.error} />}
      </form>
    </BottomSheet>
  );
}
