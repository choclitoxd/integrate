import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { useHttpClient } from '@/shared/api';

import {
  accreditLinkManager,
  changeAssociationStatus,
  changeHostStatus,
  changeLinkManagerStatus,
  createAssociation,
  createAdminExperience,
  createHostProfile,
  createUser,
  getUserDetail,
  listAdminExperiences,
  listAssociationOptions,
  listAssociations,
  listHostOptions,
  listHosts,
  listLinkManagers,
  listUsers,
  moderateExperience,
  searchAudit,
  searchUsers,
  updateAdminExperience,
  updateAssociation,
  updateUser,
  verifyAudit,
  type AccreditRequest,
  type AdminExperienceCreateRequest,
  type AdminUserUpdateRequest,
  type AssociationAction,
  type AssociationRequest,
  type AuditFilters,
  type ExperienceStatus,
  type ExperienceUpdateRequest,
  type LinkManagerStatus,
  type ManagerAction,
  type ModerationAction,
  type UserCreateRequest,
  type UserCriterion,
  type UserRole,
} from './api';

/** Bajo `['me', …]`: se borra al cerrar sesión (ADR-0013 §2). */
export const adminKeys = {
  experiences: ['me', 'admin', 'experiences'] as const,
  associations: ['me', 'admin', 'associations'] as const,
  audit: ['me', 'admin', 'audit'] as const,
  managers: ['me', 'admin', 'link-managers'] as const,
  hosts: ['me', 'admin', 'hosts'] as const,
  users: ['me', 'admin', 'users'] as const,
  detail: ['me', 'admin', 'user-detail'] as const,
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

export function useUpdateAdminExperience() {
  const http = useHttpClient();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { readonly id: string; readonly body: ExperienceUpdateRequest }) =>
      updateAdminExperience(http, id, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: adminKeys.experiences }),
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

export function useUpdateAssociation() {
  const http = useHttpClient();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { readonly id: string; readonly body: AssociationRequest }) =>
      updateAssociation(http, id, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: adminKeys.associations }),
  });
}

export function useChangeAssociationStatus() {
  const http = useHttpClient();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, action }: { readonly id: string; readonly action: AssociationAction }) =>
      changeAssociationStatus(http, id, action),
    onSettled: () => queryClient.invalidateQueries({ queryKey: adminKeys.associations }),
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

/* ---------------------------------------------------------------------------- personas (ADR-0043) */

/**
 * La búsqueda de personas es una acción explícita, no una consulta en caché: cada una queda en la
 * bitácora y cuenta para el límite de 20 por minuto, así que nunca se repite sola.
 */
export function useSearchUser() {
  const http = useHttpClient();
  return useMutation({ mutationFn: (criterion: UserCriterion) => searchUsers(http, criterion) });
}

/**
 * Listado de cuentas por rol (ADR-0048 §1). Cada consulta queda en la bitácora como acceso sensible:
 * no se refresca sola al volver a la ventana ni se conserva al salir de la pantalla.
 */
export function useUsers(role: UserRole | null, page: number) {
  const http = useHttpClient();
  return useQuery({
    queryKey: [...adminKeys.users, role ?? 'ALL', page],
    queryFn: ({ signal }) => listUsers(http, role, page, signal),
    placeholderData: keepPreviousData,
    staleTime: Infinity,
    gcTime: 0,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  });
}

export function useCreateUser() {
  const http = useHttpClient();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: UserCreateRequest) => createUser(http, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: adminKeys.users }),
  });
}

export function useCreateAdminExperience() {
  const http = useHttpClient();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: AdminExperienceCreateRequest) => createAdminExperience(http, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: adminKeys.experiences }),
  });
}

/** Historial de roles; no se conserva al salir de la pantalla (lleva datos personales). */
export function useUserDetail(userId: string) {
  const http = useHttpClient();
  return useQuery({
    queryKey: [...adminKeys.detail, userId],
    queryFn: ({ signal }) => getUserDetail(http, userId, signal),
    staleTime: Infinity,
    gcTime: 0,
    refetchOnWindowFocus: false,
  });
}

export function useLinkManagers(status: LinkManagerStatus | null, page: number) {
  const http = useHttpClient();
  return useQuery({
    queryKey: [...adminKeys.managers, status ?? 'ALL', page],
    queryFn: ({ signal }) => listLinkManagers(http, status, page, signal),
    placeholderData: keepPreviousData,
    staleTime: 0,
  });
}

export function useHosts(associationId: string | null, page: number) {
  const http = useHttpClient();
  return useQuery({
    queryKey: [...adminKeys.hosts, associationId ?? 'ALL', page],
    queryFn: ({ signal }) => listHosts(http, associationId, page, signal),
    placeholderData: keepPreviousData,
    staleTime: 0,
  });
}

/** Anfitriones para elegir al crear una experiencia (ADR-0048 §2); lleva datos personales: no se conserva. */
export function useHostOptions() {
  const http = useHttpClient();
  return useQuery({
    queryKey: [...adminKeys.hosts, 'options'],
    queryFn: ({ signal }) => listHostOptions(http, signal),
    staleTime: Infinity,
    gcTime: 0,
    refetchOnWindowFocus: false,
  });
}

export function useAssociationOptions() {
  const http = useHttpClient();
  return useQuery({
    queryKey: [...adminKeys.associations, 'options'],
    queryFn: ({ signal }) => listAssociationOptions(http, signal),
    staleTime: 60_000,
  });
}

export function useCreateHostProfile() {
  const http = useHttpClient();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: { userId: string; associationId: string }) => createHostProfile(http, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: adminKeys.hosts }),
  });
}

export function useAccreditLinkManager() {
  const http = useHttpClient();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: AccreditRequest) => accreditLinkManager(http, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: adminKeys.managers }),
  });
}

/** Tras cada intento se recarga la lista: un 409 significa que la fila ya cambió (ADR-0042 §5). */
export function useChangeManagerStatus() {
  const http = useHttpClient();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, action }: { id: string; action: ManagerAction }) =>
      changeLinkManagerStatus(http, id, action),
    onSettled: () => queryClient.invalidateQueries({ queryKey: adminKeys.managers }),
  });
}

/** Suspender, reactivar o revocar a un anfitrión; la lista se recarga tras cada intento (ADR-0070). */
export function useChangeHostStatus() {
  const http = useHttpClient();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, action, reason }: { id: string; action: ManagerAction; reason: string }) =>
      changeHostStatus(http, id, action, reason),
    onSettled: () => queryClient.invalidateQueries({ queryKey: adminKeys.hosts }),
  });
}

/** Actualiza una cuenta de usuario (ADR-0057 §1). */
export function useUpdateUser() {
  const http = useHttpClient();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: AdminUserUpdateRequest }) =>
      updateUser(http, id, body),
    onSuccess: (_updated, { id }) => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.users });
      void queryClient.invalidateQueries({ queryKey: [...adminKeys.detail, id] });
    },
  });
}
