import { useQuery } from '@tanstack/react-query';
import { useMemo } from 'react';

import { useHttpClient } from '@/shared/api';
import { hasRole, useSession } from '@/shared/session';

export type ZoneStatus = 'ACTIVE' | 'SUSPENDED' | 'REVOKED';

export interface ZoneStatuses {
  readonly gestor?: ZoneStatus | undefined;
  readonly host?: ZoneStatus | undefined;
}

interface StatusBody {
  readonly status: ZoneStatus;
}

/** El estado se considera fresco 5 minutos (ADR-0069 §5); se vuelve a consultar al iniciar sesión. */
const FIVE_MINUTES = 5 * 60_000;

/**
 * Estado de la acreditación del gestor (ADR-0069) y del perfil del anfitrión (ADR-0070). Solo se consulta lo que
 * la sesión puede tener. Mientras se consulta, o si falla (por ejemplo, sin conexión), el valor queda indefinido y
 * se muestran las herramientas como siempre: el servidor es quien niega, y un gestor en campo sin red no debe
 * perder su operación por una consulta fallida.
 */
export function useZoneStatus(): ZoneStatuses {
  const http = useHttpClient();
  const session = useSession();
  const isGestor = hasRole(session, 'ROLE_LINK_MANAGER');
  const isHost = hasRole(session, 'ROLE_HOST');
  const userId = session.status === 'authenticated' ? session.user.id : null;

  const gestor = useQuery({
    queryKey: ['me', 'zone-status', 'gestor', userId],
    queryFn: ({ signal }) => http.request<StatusBody>('/v1/gestor/accreditation', { signal }),
    enabled: isGestor,
    staleTime: FIVE_MINUTES,
    retry: false,
  });
  const host = useQuery({
    queryKey: ['me', 'zone-status', 'host', userId],
    queryFn: ({ signal }) => http.request<StatusBody>('/v1/host/status', { signal }),
    enabled: isHost,
    staleTime: FIVE_MINUTES,
    retry: false,
  });

  const gestorStatus = isGestor ? gestor.data?.status : undefined;
  const hostStatus = isHost ? host.data?.status : undefined;
  return useMemo(() => ({ gestor: gestorStatus, host: hostStatus }), [gestorStatus, hostStatus]);
}
