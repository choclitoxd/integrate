import type { DocType } from '@/features/auth';
import { NetworkError, type HttpClient } from '@/shared/api';

/** DTO del enrolamiento asistido desde `docs/api/openapi.yaml` (ADR-0007, ADR-0018). */

export type DocumentKind = 'PHYSICAL_CONSENT_FORM' | 'VICTIM_SUPPORT_DOC';

export interface DocumentUploadTicket {
  readonly s3Key: string;
  readonly uploadUrl: string;
  readonly contentType: 'application/pdf';
  readonly maxSizeBytes: number;
  readonly expiresAt: string;
}

export interface VictimData {
  readonly victimCondition: boolean;
  readonly ruvRegistrationNumber?: string;
  readonly victimizationType?: string;
  readonly supportDocS3Key?: string;
}

export interface AssistedRegistrationRequest {
  readonly fullName: string;
  readonly docType: DocType;
  readonly docNumber: string;
  readonly phone: string;
  readonly email?: string;
  readonly victimData: VictimData;
  readonly consent: {
    readonly policyVersion: string;
    readonly purposes: readonly ['PERSONAL_DATA', 'SENSITIVE_VICTIM'];
    readonly physicalConsentFormS3Key: string;
  };
}

export interface AssistedRegistration {
  readonly hostProfileId: string;
  readonly email: string;
  readonly emailIsSynthetic: boolean;
  readonly credentialStatus: 'PENDING_RESET';
}

export type CredentialStatus = 'ACTIVE' | 'PENDING_RESET';

export interface ManagedHost {
  readonly hostProfileId: string;
  readonly userId: string;
  readonly fullName: string;
  readonly email: string;
  readonly emailIsSynthetic: boolean;
  readonly phone: string;
  readonly docType: DocType;
  readonly docNumberLast4: string;
  readonly credentialStatus: CredentialStatus;
  readonly assignedSince: string;
}

export interface ManagedHostPage {
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
  readonly items: readonly ManagedHost[];
}

export interface ActivationCode {
  readonly code: string;
  readonly expiresAt: string;
}

export const HOSTS_PAGE_SIZE = 20;

export function requestUpload(
  http: HttpClient,
  kind: DocumentKind,
  signal?: AbortSignal,
): Promise<DocumentUploadTicket> {
  return http.request<DocumentUploadTicket>('/v1/gestor/uploads', {
    method: 'POST',
    body: { kind },
    ...(signal && { signal }),
  });
}

/** El PUT a S3 falló por algo distinto a la red (URL vencida, tamaño, firma). */
export class UploadRejectedError extends Error {
  readonly status: number;

  constructor(status: number) {
    super(`S3 rechazó la subida (${String(status)}).`);
    this.name = 'UploadRejectedError';
    this.status = status;
  }
}

/**
 * PUT directo a la URL prefirmada (ADR-0018 §3). Es otro origen: sin cookies ni token, y con el
 * mismo `Content-Type` con el que se firmó.
 */
export async function putToStorage(
  ticket: DocumentUploadTicket,
  file: Blob,
  signal?: AbortSignal,
  fetchImpl: typeof fetch = (input, init) => fetch(input, init),
): Promise<void> {
  let response: Response;
  try {
    response = await fetchImpl(ticket.uploadUrl, {
      method: 'PUT',
      headers: { 'Content-Type': ticket.contentType },
      body: file,
      credentials: 'omit',
      ...(signal && { signal }),
    });
  } catch (cause) {
    throw new NetworkError(cause);
  }
  if (!response.ok) throw new UploadRejectedError(response.status);
}

export function registerAssisted(
  http: HttpClient,
  body: AssistedRegistrationRequest,
): Promise<AssistedRegistration> {
  return http.request<AssistedRegistration>('/v1/auth/register-assisted', {
    method: 'POST',
    body,
  });
}

export function listManagedHosts(
  http: HttpClient,
  page: number,
  signal?: AbortSignal,
): Promise<ManagedHostPage> {
  const query = new URLSearchParams({ page: String(page), size: String(HOSTS_PAGE_SIZE) });
  return http.request<ManagedHostPage>(
    `/v1/gestor/hosts?${query.toString()}`,
    signal ? { signal } : {},
  );
}

export function issueActivationCode(
  http: HttpClient,
  hostProfileId: string,
): Promise<ActivationCode> {
  return http.request<ActivationCode>(
    `/v1/gestor/hosts/${encodeURIComponent(hostProfileId)}/activation-code`,
    { method: 'POST' },
  );
}
