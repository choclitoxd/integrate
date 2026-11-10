import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { useHttpClient } from '@/shared/api';

import {
  createAssociation,
  listAdminExperiences,
  listAssociations,
  moderateExperience,
  searchAudit,
  verifyAudit,
  type AssociationRequest,
  type AuditFilters,
  type ExperienceStatus,
  type ModerationAction,
} from './api';

/** Bajo `['me', …]`: se borra al cerrar sesión (ADR-0013 §2). */
export const adminKeys = {
  experiences: ['me', 'admin', 'experiences'] as const,
  associations: ['me', 'admin', 'associations'] as const,
  audit: ['me', 'admin', 'audit'] as const,
};

export function useAdminExperiences(status: ExperienceStatus | null, page: number) {
  const http = useHttpClient();
  return useQuery({
    queryKey: [...adminKeys.experiences, status ?? 'ALL', page],
    queryFn: ({ signal }) => listAdminExperiences(http, status, page, signal),
    placeholderData: keepPreviousData,
    staleTime: 0,
  });
}

interface Moderation {
  readonly id: string;
  readonly action: ModerationAction;
  readonly reason?: string;
}

/** Tras cada intento se recarga la cola: un 409 significa que la fila ya cambió (ADR-0042 §5). */
export function useModerateExperience() {
  const http = useHttpClient();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, action, reason }: Moderation) =>
      moderateExperience(http, id, action, reason),
    onSettled: () => queryClient.invalidateQueries({ queryKey: adminKeys.experiences }),
  });
}

export function useAssociations(page: number) {
  const http = useHttpClient();
  return useQuery({
    queryKey: [...adminKeys.associations, page],
    queryFn: ({ signal }) => listAssociations(http, page, signal),
    placeholderData: keepPreviousData,
    staleTime: 0,
  });
}

export function useCreateAssociation() {
  const http = useHttpClient();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: AssociationRequest) => createAssociation(http, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: adminKeys.associations }),
  });
}

/**
 * Cada consulta queda registrada en la propia bitácora (`AUDIT_QUERIED`): no se refresca sola al
 * volver a la ventana ni se conserva en memoria al salir de la pantalla.
 */
export function useAuditSearch(filters: AuditFilters, page: number) {
  const http = useHttpClient();
  return useQuery({
    queryKey: [...adminKeys.audit, filters, page],
    queryFn: ({ signal }) => searchAudit(http, filters, page, signal),
    placeholderData: keepPreviousData,
    staleTime: Infinity,
    gcTime: 0,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  });
}

/** La verificación de la cadena se pide a mano: también deja huella en la bitácora. */
export function useVerifyAudit() {
  const http = useHttpClient();
  return useMutation({ mutationFn: () => verifyAudit(http) });
}
