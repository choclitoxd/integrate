import { Navigate } from 'react-router';

import { accountPathFor, useSession } from '@/shared/session';

/**
 * `/cuenta` heredada (ADR-0062): lleva a «Mi cuenta» de la zona del rol. El administrador va a su
 * panel; el anfitrión o el gestor sin rol de turista, a la suya; el resto, a la de turista.
 */
export function AccountRedirect() {
  const session = useSession();
  return <Navigate to={accountPathFor(session)} replace />;
}
