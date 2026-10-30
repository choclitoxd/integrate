import { z } from 'zod/mini';

import type { HttpClient } from '@/shared/api';
import { getOrCreateDeviceId } from '@/shared/offline';

export const signingKeyDtoSchema = z.object({
  kid: z.string().check(z.minLength(1)),
  algorithm: z.enum(['RS256', 'Ed25519'] as const),
  publicKeyPem: z.string().check(z.minLength(1)),
  status: z.enum(['ACTIVE', 'RETIRED'] as const),
  validFrom: z.string(),
});

export const participantDtoSchema = z.object({
  fullName: z.string(),
  docType: z.string(),
  docNumber: z.string(),
});

export const emergencyContactDtoSchema = z.object({
  name: z.string(),
  phone: z.string(),
});

export const rosterVoucherDtoSchema = z.object({
  voucherId: z.string().check(z.minLength(1)),
  jti: z.string().check(z.minLength(1)),
  bookingId: z.string().check(z.minLength(1)),
  scheduleId: z.string().check(z.minLength(1)),
  experienceTitle: z.string(),
  shortCode: z.string().check(z.minLength(8)),
  tokenSha256: z.string().check(z.minLength(64)),
  signingKeyKid: z.string().check(z.minLength(1)),
  holderName: z.string(),
  holderDocument: z.string(),
  slotsCount: z.int().check(z.positive()),
  participants: z.array(participantDtoSchema),
  emergencyContact: emergencyContactDtoSchema,
});

export const revocationItemDtoSchema = z.object({
  voucherId: z.string().check(z.minLength(1)),
  jti: z.string().check(z.minLength(1)),
  shortCode: z.string().check(z.minLength(8)),
  revokedAt: z.string(),
  revocationReason: z.string(),
});

export const revocationsResponseSchema = z.object({
  items: z.array(revocationItemDtoSchema),
  watermark: z.nullable(z.string()),
});

export const rosterResponseSchema = z.object({
  rosterDate: z.string(),
  generatedAt: z.string(),
  associationId: z.string(),
  signingKeys: z.array(signingKeyDtoSchema),
  vouchers: z.array(rosterVoucherDtoSchema),
  revocations: revocationsResponseSchema,
});

export const redeemResponseSchema = z.object({
  voucherId: z.string(),
  bookingId: z.string(),
  slotsCount: z.int(),
  holderName: z.string(),
  experienceTitle: z.string(),
  redeemedAt: z.string(),
});

export const syncItemResultSchema = z.object({
  localValidationId: z.string(),
  voucherId: z.string(),
  status: z.enum(['CONFIRMED', 'CONFLICT', 'REJECTED'] as const),
  serverOutcome: z.string(),
  bookingStatus: z.nullable(z.string()),
  incidentType: z.nullable(z.string()),
  incidentId: z.nullable(z.string()),
  message: z.nullable(z.string()),
});

export const offlineSyncBatchResponseSchema = z.object({
  syncBatchId: z.string(),
  deviceId: z.string(),
  replayed: z.boolean(),
  deviceSentAt: z.string(),
  serverReceivedAt: z.string(),
  processedAt: z.string(),
  clockSkewSeconds: z.int(),
  summary: z.object({
    received: z.int(),
    confirmed: z.int(),
    conflicts: z.int(),
    rejected: z.int(),
  }),
  items: z.array(syncItemResultSchema),
});

export type RosterResponseDto = z.infer<typeof rosterResponseSchema>;
export type RevocationsResponseDto = z.infer<typeof revocationsResponseSchema>;
export type RedeemResponseDto = z.infer<typeof redeemResponseSchema>;
export type OfflineSyncBatchResponseDto = z.infer<typeof offlineSyncBatchResponseSchema>;

export interface SyncBatchValidationItemRequest {
  readonly localValidationId: string;
  readonly voucherId: string;
  readonly scannedAt: string;
  readonly signatureHash?: string | null;
  readonly shortCode: string;
  readonly channel: 'OFFLINE_PWA' | 'MANUAL_CODE';
}

export interface OfflineSyncBatchRequestDto {
  readonly syncBatchId: string;
  readonly deviceId: string;
  readonly deviceSentAt: string;
  readonly validations: readonly SyncBatchValidationItemRequest[];
}

function deviceHeaders(): Readonly<Record<string, string>> {
  return { 'X-Device-Id': getOrCreateDeviceId() };
}

/** Descarga preventiva del padrón de la jornada (`GET /api/v1/offline/roster`, ADR-0032 §2). */
export async function fetchOfflineRoster(
  http: HttpClient,
  params: { readonly date?: string; readonly scheduleId?: string } = {},
): Promise<RosterResponseDto> {
  const query = new URLSearchParams();
  if (params.date) query.set('date', params.date);
  if (params.scheduleId) query.set('scheduleId', params.scheduleId);
  const qs = query.toString();
  const path = qs ? `/v1/offline/roster?${qs}` : '/v1/offline/roster';
  const raw = await http.request<unknown>(path, { headers: deviceHeaders() });
  const parsed = rosterResponseSchema.safeParse(raw);
  if (!parsed.success) {
    throw new Error('La respuesta del padrón offline del servidor no es válida.');
  }
  return parsed.data;
}

/** Descarga diferencial de la lista de revocación (`GET /api/v1/offline/revocations`, ADR-0032 §3). */
export async function fetchOfflineRevocations(
  http: HttpClient,
  since?: string | null,
): Promise<RevocationsResponseDto> {
  const query = new URLSearchParams();
  if (since) query.set('since', since);
  const qs = query.toString();
  const path = qs ? `/v1/offline/revocations?${qs}` : '/v1/offline/revocations';
  const raw = await http.request<unknown>(path, { headers: deviceHeaders() });
  const parsed = revocationsResponseSchema.safeParse(raw);
  if (!parsed.success) {
    throw new Error('La lista de revocaciones del servidor no es válida.');
  }
  return parsed.data;
}

/** Canje manual de contingencia en línea (`POST /api/v1/offline/contingency/redeem`, ADR-0032 §4). */
export async function redeemContingencyOnline(
  http: HttpClient,
  shortCode: string,
): Promise<RedeemResponseDto> {
  const raw = await http.request<unknown>('/v1/offline/contingency/redeem', {
    method: 'POST',
    headers: deviceHeaders(),
    body: { shortCode },
  });
  const parsed = redeemResponseSchema.safeParse(raw);
  if (!parsed.success) {
    throw new Error('La respuesta de canje de contingencia del servidor no es válida.');
  }
  return parsed.data;
}

/** Sincronización idempotente por lotes (`POST /api/v1/offline/sync`, ADR-0033 §2). */
export async function postOfflineSyncBatch(
  http: HttpClient,
  payload: OfflineSyncBatchRequestDto,
): Promise<OfflineSyncBatchResponseDto> {
  const raw = await http.request<unknown>('/v1/offline/sync', {
    method: 'POST',
    headers: deviceHeaders(),
    body: payload,
  });
  const parsed = offlineSyncBatchResponseSchema.safeParse(raw);
  if (!parsed.success) {
    throw new Error('La respuesta de sincronización del servidor no es válida.');
  }
  return parsed.data;
}
