import type { HttpClient } from '@/shared/api';
import type { ConsentPurpose } from '@/shared/session';

/** DTO de autenticación y consentimiento desde `docs/api/openapi.yaml` (ADR-0005, ADR-0013). */

export type DocType = 'CC' | 'CE' | 'PA' | 'PPT' | 'TI';

export interface PrivacyPolicy {
  readonly version: string;
  readonly title: string;
  readonly documentUrl: string;
  readonly effectiveFrom: string;
  readonly contentSha256: string;
}

export interface ConsentAcceptance {
  readonly policyVersion: string;
  readonly purposes: readonly ConsentPurpose[];
}

export interface RegisterRequest {
  readonly email: string;
  readonly password: string;
  readonly fullName: string;
  readonly docType: DocType;
  readonly docNumber: string;
  readonly phone: string;
  readonly consent: ConsentAcceptance;
}

export type ConsentState = 'VALID' | 'OUTDATED' | 'REVOKED' | 'MISSING';

export interface ConsentStatus {
  readonly currentPolicyVersion: string | null;
  readonly consents: readonly {
    readonly purpose: ConsentPurpose;
    readonly state: ConsentState;
    readonly policyVersion: string | null;
    readonly occurredAt: string | null;
  }[];
  readonly requiresConsentUpdate: boolean;
  readonly pendingPurposes: readonly ConsentPurpose[];
}

export function getCurrentPolicy(http: HttpClient, signal?: AbortSignal): Promise<PrivacyPolicy> {
  return http.request<PrivacyPolicy>('/v1/privacy-policies/current', signal ? { signal } : {});
}

export function registerTourist(http: HttpClient, body: RegisterRequest): Promise<unknown> {
  return http.request('/v1/auth/register', { method: 'POST', body });
}

export function getMyConsents(http: HttpClient, signal?: AbortSignal): Promise<ConsentStatus> {
  return http.request<ConsentStatus>('/v1/me/consents', signal ? { signal } : {});
}

export function grantConsents(http: HttpClient, body: ConsentAcceptance): Promise<ConsentStatus> {
  return http.request<ConsentStatus>('/v1/me/consents', { method: 'POST', body });
}
