import type { HttpClient } from '@/shared/api';
import {
  assertQuotaAvailable,
  estimateSerializedBytes,
  getOfflineDB,
  notifyOfflineChanges,
  readOfflineMeta,
  touchOfflineActivity,
  updateOfflineMeta,
  type OfflineRevocationEntry,
  type OfflineRosterEntry,
  type OfflineSigningKeyEntry,
  type RosterDownloadMeta,
} from '@/shared/offline';

import {
  fetchOfflineRevocations,
  fetchOfflineRoster,
  type RevocationsResponseDto,
  type RosterResponseDto,
} from './api';

export interface RosterDownloadResult {
  readonly meta: RosterDownloadMeta;
  readonly signingKeysCount: number;
  readonly vouchersCount: number;
  readonly revocationsCount: number;
  readonly preservedLocalConsumptions: number;
}

/**
 * Descarga el padrón de la jornada y lo persiste de forma atómica en `IntegrateOfflineDB` (T-2.9, ADR-0037 §1).
 * Preserva las marcas `CONSUMIDO_LOCAL` ya registradas en el dispositivo durante la jornada.
 */
export async function downloadAndStoreRoster(
  http: HttpClient,
  options: { readonly date?: string; readonly scheduleId?: string } = {},
): Promise<RosterDownloadResult> {
  const response: RosterResponseDto = await fetchOfflineRoster(http, options);
  const additionalBytes = estimateSerializedBytes(response, true);
  await assertQuotaAvailable(additionalBytes);

  const db = await getOfflineDB();
  const tx = db.transaction(
    ['public_signing_keys', 'operational_roster', 'revocation_list'],
    'readwrite',
  );

  const keysStore = tx.objectStore('public_signing_keys');
  for (const key of response.signingKeys) {
    const entry: OfflineSigningKeyEntry = {
      kid: key.kid,
      algorithm: key.algorithm,
      publicKeyPem: key.publicKeyPem,
      status: key.status,
      validFrom: key.validFrom,
    };
    await keysStore.put(entry);
  }

  const rosterStore = tx.objectStore('operational_roster');
  const existingRoster = await rosterStore.getAll();
  const consumedByVoucherId = new Map<string, { consumedLocallyAt?: string }>();
  for (const prev of existingRoster) {
    if (prev.localStatus === 'CONSUMIDO_LOCAL') {
      consumedByVoucherId.set(prev.voucherId, {
        ...(prev.consumedLocallyAt !== undefined && {
          consumedLocallyAt: prev.consumedLocallyAt,
        }),
      });
    }
  }

  // Si es descarga completa de la fecha (sin filtro de franja), reemplaza el padrón previo conservando CONSUMIDO_LOCAL.
  if (!options.scheduleId) {
    await rosterStore.clear();
  }

  let preservedLocalConsumptions = 0;
  for (const voucher of response.vouchers) {
    const preserved = consumedByVoucherId.get(voucher.voucherId);
    if (preserved) preservedLocalConsumptions += 1;
    const entry: OfflineRosterEntry = {
      voucherId: voucher.voucherId,
      jti: voucher.jti,
      bookingId: voucher.bookingId,
      scheduleId: voucher.scheduleId,
      experienceTitle: voucher.experienceTitle,
      shortCode: voucher.shortCode,
      tokenSha256: voucher.tokenSha256,
      signingKeyKid: voucher.signingKeyKid,
      holderName: voucher.holderName,
      holderDocument: voucher.holderDocument,
      slotsCount: voucher.slotsCount,
      participants: voucher.participants,
      emergencyContact: voucher.emergencyContact,
      rosterDate: response.rosterDate,
      localStatus: preserved ? 'CONSUMIDO_LOCAL' : 'ISSUED',
      ...(preserved?.consumedLocallyAt !== undefined && {
        consumedLocallyAt: preserved.consumedLocallyAt,
      }),
    };
    await rosterStore.put(entry);
  }

  const revocationsStore = tx.objectStore('revocation_list');
  for (const rev of response.revocations.items) {
    const entry: OfflineRevocationEntry = {
      voucherId: rev.voucherId,
      jti: rev.jti,
      shortCode: rev.shortCode,
      revokedAt: rev.revokedAt,
      revocationReason: rev.revocationReason,
    };
    await revocationsStore.put(entry);
  }

  await tx.done;

  const meta: RosterDownloadMeta = {
    rosterDate: response.rosterDate,
    generatedAt: response.generatedAt,
    associationId: response.associationId,
    revocationsWatermark: response.revocations.watermark,
    voucherCount: response.vouchers.length,
  };
  updateOfflineMeta({ rosterMeta: meta });
  touchOfflineActivity();
  notifyOfflineChanges();

  return {
    meta,
    signingKeysCount: response.signingKeys.length,
    vouchersCount: response.vouchers.length,
    revocationsCount: response.revocations.items.length,
    preservedLocalConsumptions,
  };
}

/**
 * Descarga e incorpora el delta de revocación usando el último `revocationsWatermark` (T-2.9, ADR-0037 §1.4).
 */
export async function refreshRevocationDelta(
  http: HttpClient,
): Promise<{ readonly addedCount: number; readonly watermark: string | null }> {
  const currentMeta = readOfflineMeta();
  const since = currentMeta.rosterMeta?.revocationsWatermark ?? null;
  const response: RevocationsResponseDto = await fetchOfflineRevocations(http, since);
  const additionalBytes = estimateSerializedBytes(response.items, true);
  await assertQuotaAvailable(additionalBytes);

  const db = await getOfflineDB();
  const tx = db.transaction('revocation_list', 'readwrite');
  for (const rev of response.items) {
    await tx.store.put({
      voucherId: rev.voucherId,
      jti: rev.jti,
      shortCode: rev.shortCode,
      revokedAt: rev.revokedAt,
      revocationReason: rev.revocationReason,
    });
  }
  await tx.done;

  if (currentMeta.rosterMeta) {
    updateOfflineMeta({
      rosterMeta: {
        ...currentMeta.rosterMeta,
        revocationsWatermark: response.watermark ?? currentMeta.rosterMeta.revocationsWatermark,
      },
    });
  }
  touchOfflineActivity();
  notifyOfflineChanges();

  return {
    addedCount: response.items.length,
    watermark: response.watermark,
  };
}

/** Consulta los vouchers almacenados en `operational_roster`, con filtro opcional por código, documento o titular. */
export async function listLocalRoster(query = ''): Promise<readonly OfflineRosterEntry[]> {
  const db = await getOfflineDB();
  const all = await db.getAll('operational_roster');
  const trimmed = query.trim().toLowerCase();
  const filtered = trimmed
    ? all.filter(
        (item) =>
          item.shortCode.toLowerCase().includes(trimmed) ||
          item.holderName.toLowerCase().includes(trimmed) ||
          item.holderDocument.toLowerCase().includes(trimmed) ||
          item.experienceTitle.toLowerCase().includes(trimmed),
      )
    : all;
  return [...filtered].sort((a, b) => a.holderName.localeCompare(b.holderName, 'es-CO'));
}
