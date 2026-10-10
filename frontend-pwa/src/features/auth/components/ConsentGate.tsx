import { useSession, useSessionStore } from '@/shared/session';

import { ConsentDialog } from './ConsentDialog';

/**
 * Re-consentimiento bloqueante (RF-02, ADR-0013 §5): si el login o el refresh devolvieron
 * `requiresConsentUpdate`, nada se usa hasta aceptar; "No acepto" cierra la sesión.
 */
export function ConsentGate() {
  const session = useSession();
  const store = useSessionStore();
  const update = session.status === 'authenticated' ? session.consentUpdate : null;

  return (
    <ConsentDialog
      open={update !== null}
      mode="required"
      purposes={update?.purposes ?? []}
      policyVersion={update?.policyVersion}
      onGranted={() => {
        store.consentCompleted();
      }}
      onDecline={() => {
        void store.signOut();
      }}
    />
  );
}
