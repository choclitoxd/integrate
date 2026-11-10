import type { DocType } from '@/features/auth';
import type { HttpClient } from '@/shared/api';
import { addDays } from '@/shared/lib';

/** DTO de la administración desde `docs/api/openapi.yaml` (ADR-0006, ADR-0030, ADR-0035, ADR-0042). */

export const ADMIN_PAGE_SIZE = 20;

export interface Page<T> {
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
  readonly items: readonly T[];
}

export type ExperienceStatus =
  'DRAFT' | 'PENDING_REVIEW' | 'REJECTED' | 'APPROVED' | 'SUSPENDED' | 'RETIRED';

export type MeetingPointState =
  | 'PENDIENTE'
  | 'VERIFICANDO'
  | 'MATCH'
  | 'REVIEW'
  | 'MISMATCH'
  | 'UNVERIFIED'
  | 'CONFIRMADO'
  | 'CORREGIDO';

export interface MeetingPointCheck {
  readonly status: MeetingPointState;
  readonly distanceMeters: number | null;
  readonly suggestedLatitude: number | null;
  readonly suggestedLongitude: number | null;
  readonly checkedAt: string | null;
}

export interface ExperienceStop {
  readonly name: string;
  readonly description: string | null;
}

export interface AdminExperience {
  readonly id: string;
  readonly hostId: string;
  readonly title: string;
  readonly description: string;
  readonly safetyGuidelines: string | null;
  readonly physicalIntensity: 'LOW' | 'MEDIUM' | 'HIGH';
  readonly durationMinutes: number;
  readonly municipalityCode: string;
  readonly vereda: string | null;
  readonly meetingPoint: string | null;
  readonly latitude: number | null;
  readonly longitude: number | null;
  readonly stops: readonly ExperienceStop[];
  readonly maxCapacityPerSlot: number;
  readonly unitPriceCop: number;
  readonly categoryCodes: readonly string[];
  readonly status: ExperienceStatus;
  readonly rejectionReason: string | null;
  readonly createdAt: string;
  readonly meetingPointCheck: MeetingPointCheck | null;
}

/** Transiciones de moderación; `confirm-meeting-point` acepta un punto dudoso (ADR-0030 §4). */
export type ModerationAction =
  'approve' | 'reject' | 'suspend' | 'reinstate' | 'retire' | 'confirm-meeting-point';

export type AssociationType =
  'JUNTA_ACCION_COMUNAL' | 'COOPERATIVA_CAMPESINA' | 'ASOCIACION_VICTIMAS' | 'OTRA';

export interface AssociationRequest {
  readonly nit: string;
  readonly rntNumber: string;
  readonly legalName: string;
  readonly associationType: AssociationType;
  readonly municipalityCode: string;
  readonly address?: string;
  readonly contactEmail: string;
  readonly contactPhone: string;
}

export interface Association extends Omit<AssociationRequest, 'address'> {
  readonly id: string;
  readonly address: string | null;
  readonly status: 'ACTIVE' | 'SUSPENDED';
}

export type AuditSeverity = 'INFO' | 'WARNING' | 'HIGH' | 'CRITICAL';

export interface AuditRecord {
  readonly id: number;
  readonly createdAt: string;
  readonly module: string;
  readonly entityName: string | null;
  readonly entityId: string | null;
  readonly action: string;
  readonly severity: AuditSeverity;
  readonly actorId: string | null;
  readonly actorIp: string | null;
  readonly userAgent: string | null;
  /** El servidor redacta los datos sensibles al escribir (ADR-0035): nunca llegan en claro. */
  readonly details: Readonly<Record<string, unknown>> | null;
}

/** Filtros de la bitácora; las fechas son días civiles de Bogotá ("YYYY-MM-DD"), ambos inclusive. */
export interface AuditFilters {
  readonly module?: string;
  readonly action?: string;
  readonly severity?: AuditSeverity;
  readonly from?: string;
  readonly to?: string;
}

export interface AuditVerification {
  readonly intact: boolean;
  readonly fromId: number;
  readonly toId: number;
  readonly checked: number;
  readonly brokenId: number | null;
  readonly problem: string | null;
}

function withSignal(signal?: AbortSignal) {
  return signal ? { signal } : {};
}

function pageQuery(page: number): URLSearchParams {
  return new URLSearchParams({ page: String(page), size: String(ADMIN_PAGE_SIZE) });
}

export function listAdminExperiences(
  http: HttpClient,
  status: ExperienceStatus | null,
  page: number,
  signal?: AbortSignal,
): Promise<Page<AdminExperience>> {
  const query = pageQuery(page);
  if (status) query.set('status', status);
  return http.request<Page<AdminExperience>>(
    `/v1/admin/experiences?${query.toString()}`,
    withSignal(signal),
  );
}

export function moderateExperience(
  http: HttpClient,
  id: string,
  action: ModerationAction,
  reason?: string,
): Promise<AdminExperience> {
  const path =
    action === 'confirm-meeting-point' ? 'meeting-point/confirm' : encodeURIComponent(action);
  return http.request<AdminExperience>(`/v1/admin/experiences/${encodeURIComponent(id)}/${path}`, {
    method: 'POST',
    ...(reason !== undefined && { body: { reason } }),
  });
}

export function listAssociations(
  http: HttpClient,
  page: number,
  signal?: AbortSignal,
): Promise<Page<Association>> {
  return http.request<Page<Association>>(
    `/v1/admin/associations?${pageQuery(page).toString()}`,
    withSignal(signal),
  );
}

export function createAssociation(
  http: HttpClient,
  body: AssociationRequest,
): Promise<Association> {
  return http.request<Association>('/v1/admin/associations', { method: 'POST', body });
}

/** Medianoche de Bogotá (UTC−5, sin horario de verano) como instante ISO. */
function bogotaMidnight(isoDate: string): string {
  return new Date(`${isoDate}T00:00:00-05:00`).toISOString();
}

/** Parámetros de `/admin/audit`: `to` es exclusivo en el contrato, así que se envía el día siguiente. */
export function auditQuery(filters: AuditFilters): URLSearchParams {
  const query = new URLSearchParams();
  if (filters.module) query.set('module', filters.module);
  if (filters.action) query.set('action', filters.action);
  if (filters.severity) query.set('severity', filters.severity);
  if (filters.from) query.set('from', bogotaMidnight(filters.from));
  if (filters.to) query.set('to', bogotaMidnight(addDays(filters.to, 1)));
  return query;
}

export function searchAudit(
  http: HttpClient,
  filters: AuditFilters,
  page: number,
  signal?: AbortSignal,
): Promise<Page<AuditRecord>> {
  const query = auditQuery(filters);
  query.set('page', String(page));
  query.set('size', String(ADMIN_PAGE_SIZE));
  return http.request<Page<AuditRecord>>(`/v1/admin/audit?${query.toString()}`, withSignal(signal));
}

/** CSV de la bitácora con los mismos filtros; el servidor registra la exportación. */
export function exportAudit(http: HttpClient, filters: AuditFilters): Promise<Blob> {
  const query = auditQuery(filters).toString();
  return http.request<Blob>(`/v1/admin/audit/export${query ? `?${query}` : ''}`, {
    responseType: 'blob',
  });
}

export function verifyAudit(http: HttpClient): Promise<AuditVerification> {
  return http.request<AuditVerification>('/v1/admin/audit/verify');
}

/* --------------------------------------------------------------------------------- personas (ADR-0043) */

export type LinkManagerStatus = 'ACTIVE' | 'SUSPENDED' | 'REVOKED';

/** Cuenta encontrada por la búsqueda: sin celular ni documento completo. */
export interface AdminUser {
  readonly userId: string;
  readonly fullName: string;
  /** En claro si se buscó por correo; enmascarado (`j***@dominio`) si se buscó por documento. */
  readonly email: string;
  readonly emailMasked: boolean;
  readonly status: string;
  /** Códigos sin prefijo: `TOURIST`, `HOST`, `LINK_MANAGER`, `ADMIN`. */
  readonly roles: readonly string[];
  readonly hostProfileId: string | null;
  readonly linkManagerId: string | null;
  readonly linkManagerStatus: LinkManagerStatus | null;
}

export interface RoleHistoryEntry {
  readonly role: string;
  readonly revokedAt: string | null;
  readonly revokedBy: string | null;
}

export interface AdminUserDetail {
  readonly userId: string;
  readonly fullName: string;
  readonly maskedEmail: string;
  readonly status: string;
  readonly docType: DocType;
  readonly docNumberLast4: string;
  readonly roles: readonly string[];
  readonly hostProfileId: string | null;
  readonly linkManagerId: string | null;
  readonly linkManagerStatus: LinkManagerStatus | null;
  readonly roleHistory: readonly RoleHistoryEntry[];
}

export interface AdminLinkManager {
  readonly linkManagerId: string;
  readonly userId: string;
  readonly fullName: string;
  readonly email: string | null;
  readonly associationId: string;
  readonly associationName: string | null;
  readonly accreditationStatus: LinkManagerStatus;
  readonly accreditedOn: string;
  readonly accreditedByName: string | null;
}

export interface AdminHost {
  readonly hostProfileId: string;
  readonly userId: string;
  readonly fullName: string;
  readonly email: string | null;
  readonly emailIsSynthetic: boolean;
  readonly associationId: string;
  readonly associationName: string | null;
  readonly verified: boolean;
  readonly createdAt: string;
}

/** Exactamente un criterio: correo exacto, o tipo y número de documento exactos. */
export type UserCriterion =
  | { readonly kind: 'email'; readonly email: string }
  | { readonly kind: 'document'; readonly docType: DocType; readonly document: string };

export type ManagerAction = 'suspend' | 'reinstate' | 'revoke';

export interface AccreditRequest {
  readonly userId: string;
  readonly associationId: string;
  /** Día civil de Bogotá ("YYYY-MM-DD"); la API rechaza fechas futuras. */
  readonly accreditedOn: string;
}

export function searchUsers(http: HttpClient, criterion: UserCriterion): Promise<Page<AdminUser>> {
  const query =
    criterion.kind === 'email'
      ? new URLSearchParams({ email: criterion.email })
      : new URLSearchParams({ docType: criterion.docType, document: criterion.document });
  return http.request<Page<AdminUser>>(`/v1/admin/users/search?${query.toString()}`);
}

export function getUserDetail(
  http: HttpClient,
  userId: string,
  signal?: AbortSignal,
): Promise<AdminUserDetail> {
  return http.request<AdminUserDetail>(
    `/v1/admin/users/${encodeURIComponent(userId)}`,
    withSignal(signal),
  );
}

export function listLinkManagers(
  http: HttpClient,
  status: LinkManagerStatus | null,
  page: number,
  signal?: AbortSignal,
): Promise<Page<AdminLinkManager>> {
  const query = pageQuery(page);
  if (status) query.set('status', status);
  return http.request<Page<AdminLinkManager>>(
    `/v1/admin/link-managers?${query.toString()}`,
    withSignal(signal),
  );
}

export function listHosts(
  http: HttpClient,
  associationId: string | null,
  page: number,
  signal?: AbortSignal,
): Promise<Page<AdminHost>> {
  const query = pageQuery(page);
  if (associationId) query.set('associationId', associationId);
  return http.request<Page<AdminHost>>(
    `/v1/admin/host-profiles?${query.toString()}`,
    withSignal(signal),
  );
}

/** Asociaciones elegibles en los diálogos: la primera página del tamaño máximo (50). */
export function listAssociationOptions(
  http: HttpClient,
  signal?: AbortSignal,
): Promise<Page<Association>> {
  return http.request<Page<Association>>(
    '/v1/admin/associations?page=0&size=50',
    withSignal(signal),
  );
}

export function createHostProfile(
  http: HttpClient,
  body: { readonly userId: string; readonly associationId: string },
): Promise<{ readonly hostProfileId: string; readonly userId: string }> {
  return http.request('/v1/admin/host-profiles', { method: 'POST', body });
}

export function accreditLinkManager(
  http: HttpClient,
  body: AccreditRequest,
): Promise<{ readonly linkManagerId: string; readonly accreditationStatus: LinkManagerStatus }> {
  return http.request('/v1/admin/link-managers', { method: 'POST', body });
}

export function changeLinkManagerStatus(
  http: HttpClient,
  linkManagerId: string,
  action: ManagerAction,
): Promise<{ readonly linkManagerId: string; readonly accreditationStatus: LinkManagerStatus }> {
  return http.request(`/v1/admin/link-managers/${encodeURIComponent(linkManagerId)}/${action}`, {
    method: 'POST',
  });
}
