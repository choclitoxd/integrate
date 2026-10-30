import { ApiError, NetworkError, type HttpClient } from '@/shared/api';
import {
  allocateSequenceNumber,
  assertQuotaAvailable,
  bogotaDateString,
  getOfflineDB,
  getOrCreateDeviceId,
  notifyOfflineChanges,
  readOfflineMeta,
  requestBackgroundSync,
  touchOfflineActivity,
  type OfflineRosterEntry,
  type OfflineRosterParticipant,
  type OfflineValidationChannel,
  type OfflineValidationEntry,
  type SyncOutboxEntry,
} from '@/shared/offline';

import { redeemContingencyOnline } from './api';
import { parseCompactVoucherJws, verifyVoucherJwsSignature } from './cryptoVerifier';

export const SHORT_CODE_REGEX = /^[A-HJ-NP-Z2-9]{8}$/;

export type RedemptionVerdictCode =
  | 'ACCEPTED'
  | 'REJECTED_INVALID_FORMAT'
  | 'REJECTED_UNKNOWN_KEY'
  | 'REJECTED_INVALID_SIGNATURE'
  | 'REJECTED_REVOKED'
  | 'REJECTED_ALREADY_CONSUMED'
  | 'REJECTED_WRONG_DATE'
  | 'REJECTED_NOT_IN_ROSTER';

export interface RedemptionVoucherSummary {
  readonly voucherId: string;
  readonly bookingId: string;
  readonly shortCode: string;
  readonly holderName: string;
  readonly holderDocument: string;
  readonly experienceTitle: string;
  readonly slotsCount: number;
  readonly participants: readonly OfflineRosterParticipant[];
  readonly emergencyContact: {
    readonly name: string;
    readonly phone: string;
  };
}

export interface RedemptionResult {
  readonly accepted: boolean;
  readonly code: RedemptionVerdictCode;
  readonly title: string;
  readonly message: string;
  readonly channel: OfflineValidationChannel;
  readonly mode: 'OFFLINE' | 'ONLINE';
  readonly scannedAt: string;
  readonly previousConsumedAt?: string;
  readonly localValidationId?: string;
  readonly voucher?: RedemptionVoucherSummary;
}

/**
 * Retroalimentación háptica multimodal en campo (`navigator.vibrate`, ADR-0037 §2.5, RNF-14).
 * - Aceptado: pulso único firme de `200 ms`.
 * - Rechazado: doble pulso de alerta `[120, 80, 120] ms`.
 */
export function triggerValidationHaptics(accepted: boolean): void {
  if (typeof navigator === 'undefined' || typeof navigator.vibrate !== 'function') {
    return;
  }
  try {
    navigator.vibrate(accepted ? 200 : [120, 80, 120]);
  } catch {
    // Algunos navegadores bloquean vibrate sin gesto previo; no debe interrumpir el flujo.
  }
}

let inMemoryLockChain: Promise<unknown> = Promise.resolve();

async function withExclusiveRedeemLock<T>(task: () => Promise<T>): Promise<T> {
  const locks = (
    globalThis.navigator as {
      locks?: {
        request: <R>(
          name: string,
          options: { mode: 'exclusive' },
          cb: () => Promise<R>,
        ) => Promise<R>;
      };
    }
  ).locks;
  if (locks) {
    return locks.request('integrate-offline-redeem', { mode: 'exclusive' }, task);
  }
  const next = inMemoryLockChain.then(task, task);
  inMemoryLockChain = next.then(
    () => undefined,
    () => undefined,
  );
  return next;
}

function toSummary(entry: OfflineRosterEntry): RedemptionVoucherSummary {
  return {
    voucherId: entry.voucherId,
    bookingId: entry.bookingId,
    shortCode: entry.shortCode,
    holderName: entry.holderName,
    holderDocument: entry.holderDocument,
    experienceTitle: entry.experienceTitle,
    slotsCount: entry.slotsCount,
    participants: entry.participants,
    emergencyContact: entry.emergencyContact,
  };
}

/**
 * Ejecuta el consumo atómico local (`CONSUMIDO_LOCAL`) y el encolado en `sync_outbox` dentro de una
 * única transacción `readwrite` en IndexedDB serializada por bloqueo exclusivo (T-2.11, ADR-0037 §3, RNF-18).
 */
export async function redeemVoucherAtomically(params: {
  readonly voucherId: string;
  readonly channel: OfflineValidationChannel;
  readonly signatureSha256: string | null;
  readonly validatedBy: string;
  readonly scannedAt?: string;
}): Promise<RedemptionResult> {
  return withExclusiveRedeemLock(async () => {
    const scannedAt = params.scannedAt ?? new Date().toISOString();
    await assertQuotaAvailable(2048);

    const db = await getOfflineDB();
    const tx = db.transaction(
      ['operational_roster', 'local_validations', 'sync_outbox'],
      'readwrite',
    );
    const rosterStore = tx.objectStore('operational_roster');
    const validationsStore = tx.objectStore('local_validations');
    const outboxStore = tx.objectStore('sync_outbox');

    const rosterEntry = await rosterStore.get(params.voucherId);

    if (!rosterEntry) {
      await tx.done;
      triggerValidationHaptics(false);
      return {
        accepted: false,
        code: 'REJECTED_NOT_IN_ROSTER',
        title: 'Voucher fuera del padrón de hoy',
        message: 'Este voucher no figura en el padrón descargado en este dispositivo.',
        channel: params.channel,
        mode: 'OFFLINE',
        scannedAt,
      };
    }

    const existingValidation = await validationsStore
      .index('idx_val_voucher_id')
      .get(rosterEntry.jti);

    if (rosterEntry.localStatus === 'CONSUMIDO_LOCAL' || existingValidation !== undefined) {
      await tx.done;
      const previousConsumedAt =
        existingValidation?.scannedAt ?? rosterEntry.consumedLocallyAt ?? scannedAt;
      triggerValidationHaptics(false);
      return {
        accepted: false,
        code: 'REJECTED_ALREADY_CONSUMED',
        title: 'Voucher ya utilizado en este dispositivo',
        message: `El voucher de ${rosterEntry.holderName} ya fue consumido previamente en este teléfono.`,
        channel: params.channel,
        mode: 'OFFLINE',
        scannedAt,
        previousConsumedAt,
        voucher: toSummary(rosterEntry),
      };
    }

    const localValidationId = crypto.randomUUID();
    const mutationId = crypto.randomUUID();
    const sequenceNumber = allocateSequenceNumber();
    const deviceId = getOrCreateDeviceId();

    const updatedRoster: OfflineRosterEntry = {
      ...rosterEntry,
      localStatus: 'CONSUMIDO_LOCAL',
      consumedLocallyAt: scannedAt,
    };

    const validationEntry: OfflineValidationEntry = {
      localValidationId,
      voucherId: rosterEntry.jti,
      shortCode: rosterEntry.shortCode,
      deviceId,
      validatedBy: params.validatedBy,
      channel: params.channel,
      localResult: 'VALIDATED_OFFLINE',
      consumptionMarker: 'CONSUMIDO_LOCAL',
      signatureSha256: params.signatureSha256,
      scannedAt,
      syncStatus: 'PENDING',
    };

    const outboxEntry: SyncOutboxEntry = {
      mutationId,
      sequenceNumber,
      syncBatchId: null,
      aggregateType: 'OFFLINE_VALIDATION',
      aggregateId: localValidationId,
      actionType: 'CREATE_VALIDATION',
      payload: {
        localValidationId,
        voucherId: rosterEntry.jti,
        rosterVoucherId: rosterEntry.voucherId,
        shortCode: rosterEntry.shortCode,
        channel: params.channel,
        signatureSha256: params.signatureSha256,
        scannedAt,
      },
      status: 'QUEUED',
      retryCount: 0,
      maxRetries: 5,
      nextRetryAt: 0,
      lastError: null,
      createdAt: scannedAt,
      updatedAt: scannedAt,
    };

    await Promise.all([
      rosterStore.put(updatedRoster),
      validationsStore.put(validationEntry),
      outboxStore.put(outboxEntry),
    ]);

    await tx.done;

    touchOfflineActivity();
    notifyOfflineChanges();
    void requestBackgroundSync();
    triggerValidationHaptics(true);

    return {
      accepted: true,
      code: 'ACCEPTED',
      title: 'Ingreso autorizado',
      message: `Asistencia registrada para ${rosterEntry.holderName} (${String(
        rosterEntry.slotsCount,
      )} ${rosterEntry.slotsCount === 1 ? 'cupo' : 'cupos'}).`,
      channel: params.channel,
      mode: 'OFFLINE',
      scannedAt,
      localValidationId,
      voucher: toSummary(updatedRoster),
    };
  });
}

/**
 * Verifica criptográficamente un QR (JWS compacto) usando Web Crypto API y ejecuta la redención
 * local atómica si es auténtico y vigente (T-2.10, T-2.11, ADR-0037 §2-§3, RNF-05, RNF-14, RNF-18).
 */
export async function validateAndRedeemQrToken(
  rawToken: string,
  validatedBy: string,
  options: { readonly referenceDate?: string; readonly now?: Date } = {},
): Promise<RedemptionResult> {
  const now = options.now ?? new Date();
  const scannedAt = now.toISOString();

  let parsed;
  try {
    parsed = parseCompactVoucherJws(rawToken);
  } catch {
    triggerValidationHaptics(false);
    return {
      accepted: false,
      code: 'REJECTED_INVALID_FORMAT',
      title: 'Código QR no válido',
      message:
        'El código leído no tiene el formato criptográfico de un voucher oficial de Intégrate.',
      channel: 'OFFLINE_PWA',
      mode: 'OFFLINE',
      scannedAt,
    };
  }

  const db = await getOfflineDB();
  const signingKey = await db.get('public_signing_keys', parsed.header.kid);
  if (!signingKey) {
    triggerValidationHaptics(false);
    return {
      accepted: false,
      code: 'REJECTED_UNKNOWN_KEY',
      title: 'Llave de firma desconocida',
      message: `No se encontró la clave pública (${parsed.header.kid}) en el dispositivo. Descarga el padrón de la jornada.`,
      channel: 'OFFLINE_PWA',
      mode: 'OFFLINE',
      scannedAt,
    };
  }

  const verification = await verifyVoucherJwsSignature(parsed, signingKey);
  if (!verification.valid) {
    triggerValidationHaptics(false);
    return {
      accepted: false,
      code: 'REJECTED_INVALID_SIGNATURE',
      title: 'Firma criptográfica inválida',
      message:
        'Alerta de seguridad: la firma digital del código QR no es auténtica o fue alterada.',
      channel: 'OFFLINE_PWA',
      mode: 'OFFLINE',
      scannedAt,
    };
  }

  const revoked = await db.getFromIndex(
    'revocation_list',
    'idx_revocation_jti',
    parsed.payload.jti,
  );
  if (revoked) {
    triggerValidationHaptics(false);
    return {
      accepted: false,
      code: 'REJECTED_REVOKED',
      title: 'Voucher revocado o cancelado',
      message: `Esta reserva fue cancelada o revocada (${revoked.revocationReason}). No se permite el ingreso.`,
      channel: 'OFFLINE_PWA',
      mode: 'OFFLINE',
      scannedAt,
    };
  }

  // La fecha válida se compara con HOY en Bogotá corregido por el desfase de reloj (ADR-0037 §2.3), no con la fecha del
  // padrón descargado: un padrón de ayer no debe aceptar los vouchers de ayer. `referenceDate` solo existe para pruebas.
  const meta = readOfflineMeta();
  const adjustedNow = new Date(now.getTime() + meta.clockSkewSeconds * 1000);
  const expectedDate = options.referenceDate ?? bogotaDateString(adjustedNow);

  if (parsed.payload.vd !== expectedDate) {
    triggerValidationHaptics(false);
    return {
      accepted: false,
      code: 'REJECTED_WRONG_DATE',
      title: 'Fecha de jornada no coincide',
      message: `El voucher corresponde a la fecha ${parsed.payload.vd} y hoy es ${expectedDate}.`,
      channel: 'OFFLINE_PWA',
      mode: 'OFFLINE',
      scannedAt,
    };
  }

  // `exp` (fin de la franja + 24 h) vencido: el voucher dejó de ser válido aunque la fecha coincida (ADR-0037 §2.3.4).
  if (parsed.payload.exp * 1000 <= adjustedNow.getTime()) {
    triggerValidationHaptics(false);
    return {
      accepted: false,
      code: 'REJECTED_WRONG_DATE',
      title: 'Voucher vencido',
      message: 'La vigencia de este voucher terminó. No se permite el ingreso.',
      channel: 'OFFLINE_PWA',
      mode: 'OFFLINE',
      scannedAt,
    };
  }

  const rosterEntry = await db.getFromIndex(
    'operational_roster',
    'idx_roster_jti',
    parsed.payload.jti,
  );
  if (!rosterEntry) {
    triggerValidationHaptics(false);
    return {
      accepted: false,
      code: 'REJECTED_NOT_IN_ROSTER',
      title: 'Voucher no encontrado en el padrón',
      message:
        'La firma es auténtica, pero este voucher no está en el padrón de este teléfono (¿se compró después de descargarlo?). ' +
        'Con señal, descarga el padrón otra vez o canjéalo en línea por su código de 8 caracteres; sin señal, ' +
        'verifica el documento del titular y registra el ingreso cuando recuperes cobertura.',
      channel: 'OFFLINE_PWA',
      mode: 'OFFLINE',
      scannedAt,
    };
  }

  if (rosterEntry.tokenSha256.toLowerCase() !== verification.tokenSha256.toLowerCase()) {
    triggerValidationHaptics(false);
    return {
      accepted: false,
      code: 'REJECTED_INVALID_SIGNATURE',
      title: 'Resumen criptográfico no coincide',
      message: 'El contenido del token no coincide con el hash registrado en el padrón oficial.',
      channel: 'OFFLINE_PWA',
      mode: 'OFFLINE',
      scannedAt,
    };
  }

  return redeemVoucherAtomically({
    voucherId: rosterEntry.voucherId,
    channel: 'OFFLINE_PWA',
    signatureSha256: verification.signatureSha256,
    validatedBy,
    scannedAt,
  });
}

/** Normaliza un código alfanumérico corto (`shortCode`) eliminando guiones y espacios. */
export function normalizeShortCode(raw: string): string {
  return raw.replace(/[\s-]/g, '').toUpperCase();
}

/**
 * Canje de contingencia manual por `shortCode` de 8 caracteres (T-2.12, ADR-0037 §4, RNF-20).
 * Opera contra `/api/v1/offline/contingency/redeem` si hay conectividad o contra `IntegrateOfflineDB`
 * cuando el dispositivo está en campo sin red.
 */
export async function redeemByManualShortCode(params: {
  readonly shortCode: string;
  readonly validatedBy: string;
  readonly http?: HttpClient;
  readonly online?: boolean;
}): Promise<RedemptionResult> {
  const scannedAt = new Date().toISOString();
  const normalized = normalizeShortCode(params.shortCode);

  if (!SHORT_CODE_REGEX.test(normalized)) {
    triggerValidationHaptics(false);
    return {
      accepted: false,
      code: 'REJECTED_INVALID_FORMAT',
      title: 'Código corto inválido',
      message:
        'Ingresa los 8 caracteres exactos del voucher (letras y números sin O, I, L, 0 ni 1).',
      channel: 'MANUAL_CODE',
      mode: params.online ? 'ONLINE' : 'OFFLINE',
      scannedAt,
    };
  }

  if (params.online && params.http) {
    try {
      const onlineResult = await redeemContingencyOnline(params.http, normalized);
      const db = await getOfflineDB();
      const existingRoster = await db.getFromIndex(
        'operational_roster',
        'idx_roster_short_code',
        normalized,
      );

      if (existingRoster) {
        const tx = db.transaction(['operational_roster', 'local_validations'], 'readwrite');
        await tx.objectStore('operational_roster').put({
          ...existingRoster,
          localStatus: 'CONSUMIDO_LOCAL',
          consumedLocallyAt: onlineResult.redeemedAt,
        });
        await tx.objectStore('local_validations').put({
          localValidationId: crypto.randomUUID(),
          voucherId: existingRoster.jti,
          shortCode: existingRoster.shortCode,
          deviceId: getOrCreateDeviceId(),
          validatedBy: params.validatedBy,
          channel: 'MANUAL_CODE',
          localResult: 'VALIDATED_OFFLINE',
          consumptionMarker: 'CONSUMIDO_LOCAL',
          signatureSha256: null,
          scannedAt: onlineResult.redeemedAt,
          syncStatus: 'SYNCED',
        });
        await tx.done;
        notifyOfflineChanges();
      }

      triggerValidationHaptics(true);
      return {
        accepted: true,
        code: 'ACCEPTED',
        title: 'Ingreso autorizado en línea',
        message: `Asistencia confirmada en el servidor para ${onlineResult.holderName} (${String(
          onlineResult.slotsCount,
        )} ${onlineResult.slotsCount === 1 ? 'cupo' : 'cupos'}).`,
        channel: 'MANUAL_CODE',
        mode: 'ONLINE',
        scannedAt: onlineResult.redeemedAt,
        voucher: existingRoster
          ? toSummary(existingRoster)
          : {
              voucherId: onlineResult.voucherId,
              bookingId: onlineResult.bookingId,
              shortCode: normalized,
              holderName: onlineResult.holderName,
              holderDocument: 'Verificado en línea',
              experienceTitle: onlineResult.experienceTitle,
              slotsCount: onlineResult.slotsCount,
              participants: [],
              emergencyContact: { name: 'Registrado en reserva', phone: '—' },
            },
      };
    } catch (err) {
      if (err instanceof ApiError) {
        triggerValidationHaptics(false);
        const errorCode = err.code ?? '';
        if (errorCode === 'VOUCHER_REVOKED') {
          return {
            accepted: false,
            code: 'REJECTED_REVOKED',
            title: 'Voucher revocado',
            message: err.message,
            channel: 'MANUAL_CODE',
            mode: 'ONLINE',
            scannedAt,
          };
        }
        if (errorCode === 'VOUCHER_ALREADY_REDEEMED') {
          return {
            accepted: false,
            code: 'REJECTED_ALREADY_CONSUMED',
            title: 'Voucher ya canjeado',
            message: err.message,
            channel: 'MANUAL_CODE',
            mode: 'ONLINE',
            scannedAt,
          };
        }
        if (errorCode === 'VOUCHER_WRONG_DATE' || errorCode === 'VOUCHER_EXPIRED') {
          return {
            accepted: false,
            code: 'REJECTED_WRONG_DATE',
            title: 'Voucher fuera de fecha',
            message: err.message,
            channel: 'MANUAL_CODE',
            mode: 'ONLINE',
            scannedAt,
          };
        }
        return {
          accepted: false,
          code: 'REJECTED_NOT_IN_ROSTER',
          title: 'No se pudo canjear el código',
          message: err.message,
          channel: 'MANUAL_CODE',
          mode: 'ONLINE',
          scannedAt,
        };
      }
      if (!(err instanceof NetworkError)) {
        throw err;
      }
      // Si es NetworkError, degrada de forma transparente a validación local offline.
    }
  }

  const db = await getOfflineDB();
  const revoked = await db.getFromIndex('revocation_list', 'idx_revocation_short_code', normalized);
  if (revoked) {
    triggerValidationHaptics(false);
    return {
      accepted: false,
      code: 'REJECTED_REVOKED',
      title: 'Voucher revocado o cancelado',
      message: `El código ${normalized} figura como cancelado (${revoked.revocationReason}).`,
      channel: 'MANUAL_CODE',
      mode: 'OFFLINE',
      scannedAt,
    };
  }

  const rosterEntry = await db.getFromIndex(
    'operational_roster',
    'idx_roster_short_code',
    normalized,
  );
  if (!rosterEntry) {
    triggerValidationHaptics(false);
    return {
      accepted: false,
      code: 'REJECTED_NOT_IN_ROSTER',
      title: 'Código no encontrado en el padrón',
      message: `El código ${normalized} no aparece en el padrón descargado para hoy.`,
      channel: 'MANUAL_CODE',
      mode: 'OFFLINE',
      scannedAt,
    };
  }

  return redeemVoucherAtomically({
    voucherId: rosterEntry.voucherId,
    channel: 'MANUAL_CODE',
    signatureSha256: null,
    validatedBy: params.validatedBy,
    scannedAt,
  });
}
