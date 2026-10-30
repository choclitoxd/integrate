import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError, NetworkError, type HttpClient } from '@/shared/api';
import {
  bogotaDateString,
  clearAllOfflineStores,
  getOfflineDB,
  readOfflineMeta,
} from '@/shared/offline';
import { renderApp } from '@/test/renderApp';

import {
  bytesToBase64Url,
  classifySyncFailure,
  clearCryptoKeyCache,
  computeBackoffDelayMs,
  downloadAndStoreRoster,
  flushSyncOutbox,
  redeemByManualShortCode,
  refreshRevocationDelta,
  requeueDeadLetters,
  SYNC_MAX_RETRIES,
  sha256Hex,
  validateAndRedeemQrToken,
  type RosterResponseDto,
} from './index';

function toBase64UrlString(text: string): string {
  return bytesToBase64Url(new TextEncoder().encode(text));
}

async function generateTestRsaKeyPair(): Promise<{
  readonly privateKey: CryptoKey;
  readonly publicKeyPem: string;
}> {
  const pair = await crypto.subtle.generateKey(
    {
      name: 'RSASSA-PKCS1-v1_5',
      modulusLength: 2048,
      publicExponent: new Uint8Array([1, 0, 1]),
      hash: 'SHA-256',
    },
    true,
    ['sign', 'verify'],
  );
  const spkiBuffer = await crypto.subtle.exportKey('spki', pair.publicKey);
  const spkiBytes = new Uint8Array(spkiBuffer);
  let binary = '';
  for (const byte of spkiBytes) {
    binary += String.fromCharCode(byte);
  }
  const b64 = btoa(binary);
  const publicKeyPem = `-----BEGIN PUBLIC KEY-----\n${b64}\n-----END PUBLIC KEY-----`;
  return { privateKey: pair.privateKey, publicKeyPem };
}

async function signTestVoucherJws(
  privateKey: CryptoKey,
  kid: string,
  claims: {
    readonly jti: string;
    readonly bid: string;
    readonly sid: string;
    readonly xid: string;
    readonly vd: string;
    readonly doc: string;
    readonly slots: number;
    readonly exp?: number;
  },
): Promise<{ readonly token: string; readonly tokenSha256: string }> {
  const headerB64 = toBase64UrlString(JSON.stringify({ alg: 'RS256', typ: 'voucher+jwt', kid }));
  const payloadB64 = toBase64UrlString(
    JSON.stringify({
      iss: 'integrate',
      // `exp` dinámico: el motor rechaza los vouchers vencidos respecto del reloj real (ADR-0037 §2.3.4).
      iat: Math.floor(Date.now() / 1000) - 3_600,
      exp: Math.floor(Date.now() / 1000) + 86_400,
      ...claims,
    }),
  );
  const signingInput = new TextEncoder().encode(`${headerB64}.${payloadB64}`);
  const copy = new Uint8Array(new ArrayBuffer(signingInput.byteLength));
  copy.set(signingInput);

  const signatureBuffer = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', privateKey, copy);
  const signatureB64 = bytesToBase64Url(new Uint8Array(signatureBuffer));
  const token = `${headerB64}.${payloadB64}.${signatureB64}`;
  const tokenSha256 = await sha256Hex(token);
  return { token, tokenSha256 };
}

describe('Fase 2.2 · Padrón, validación QR, contingencia y sincronización PWA (T-2.9 a T-2.13)', () => {
  beforeEach(async () => {
    clearCryptoKeyCache();
    await clearAllOfflineStores();
    localStorage.clear();
  });

  it('T-2.9: descarga el padrón, preserva marcas CONSUMIDO_LOCAL y aplica el delta de revocación', async () => {
    const { privateKey, publicKeyPem } = await generateTestRsaKeyPair();
    const signed = await signTestVoucherJws(privateKey, 'kid-2026-10', {
      jti: '11111111-1111-4111-8111-111111111111',
      bid: '22222222-2222-4222-8222-222222222222',
      sid: '33333333-3333-4333-8333-333333333333',
      xid: '44444444-4444-4444-8444-444444444444',
      vd: '2026-10-30',
      doc: 'CC:1094000111',
      slots: 2,
    });

    const rosterDto: RosterResponseDto = {
      rosterDate: '2026-10-30',
      generatedAt: '2026-10-30T10:00:00Z',
      associationId: '55555555-5555-4555-8555-555555555555',
      signingKeys: [
        {
          kid: 'kid-2026-10',
          algorithm: 'RS256',
          publicKeyPem,
          status: 'ACTIVE',
          validFrom: '2026-10-01T00:00:00Z',
        },
      ],
      vouchers: [
        {
          voucherId: '66666666-6666-4666-8666-666666666666',
          jti: '11111111-1111-4111-8111-111111111111',
          bookingId: '22222222-2222-4222-8222-222222222222',
          scheduleId: '33333333-3333-4333-8333-333333333333',
          experienceTitle: 'Ruta del Café Agroecológico',
          shortCode: 'K7M9P2X4',
          tokenSha256: signed.tokenSha256,
          signingKeyKid: 'kid-2026-10',
          holderName: 'María Fernanda Gómez',
          holderDocument: 'CC 1094000111',
          slotsCount: 2,
          participants: [
            { fullName: 'María Fernanda Gómez', docType: 'CC', docNumber: '1094000111' },
            { fullName: 'Carlos Andrés Gómez', docType: 'CC', docNumber: '1094000222' },
          ],
          emergencyContact: { name: 'Lucía Gómez', phone: '+573001112233' },
        },
      ],
      revocations: {
        items: [],
        watermark: '2026-10-30T10:00:00Z',
      },
    };

    const fakeHttp = {
      request: vi.fn((path: string) => {
        if (path.startsWith('/v1/offline/roster')) return Promise.resolve(rosterDto);
        if (path.startsWith('/v1/offline/revocations')) {
          return Promise.resolve({
            items: [
              {
                voucherId: '99999999-9999-4999-8999-999999999999',
                jti: '88888888-8888-4888-8888-888888888888',
                shortCode: 'R3V9K2M4',
                revokedAt: '2026-10-30T10:15:00Z',
                revocationReason: 'BOOKING_CANCELLED',
              },
            ],
            watermark: '2026-10-30T10:15:00Z',
          });
        }
        throw new Error(`Unexpected path ${path}`);
      }),
    } as unknown as HttpClient;

    const firstDownload = await downloadAndStoreRoster(fakeHttp);
    expect(firstDownload.vouchersCount).toBe(1);
    expect(firstDownload.signingKeysCount).toBe(1);

    // Consume el voucher y vuelve a descargar el padrón: debe preservar CONSUMIDO_LOCAL.
    const redemption = await validateAndRedeemQrToken(signed.token, 'gestor-1', {
      referenceDate: '2026-10-30',
    });
    expect(redemption.accepted).toBe(true);

    const secondDownload = await downloadAndStoreRoster(fakeHttp);
    expect(secondDownload.preservedLocalConsumptions).toBe(1);

    const db = await getOfflineDB();
    const storedRoster = await db.get('operational_roster', '66666666-6666-4666-8666-666666666666');
    expect(storedRoster?.localStatus).toBe('CONSUMIDO_LOCAL');

    const delta = await refreshRevocationDelta(fakeHttp);
    expect(delta.addedCount).toBe(1);
    expect(readOfflineMeta().rosterMeta?.revocationsWatermark).toBe('2026-10-30T10:15:00Z');
  });

  it('T-2.10 y T-2.11: verifica firma RS256, rechaza adulterados/revocados y bloquea doble escaneo concurrente', async () => {
    const vibrateSpy = vi.fn(() => true);
    Object.defineProperty(navigator, 'vibrate', {
      value: vibrateSpy,
      configurable: true,
      writable: true,
    });

    const { privateKey, publicKeyPem } = await generateTestRsaKeyPair();
    const validSigned = await signTestVoucherJws(privateKey, 'kid-2026-10', {
      jti: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
      bid: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
      sid: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
      xid: 'dddddddd-dddd-4ddd-8ddd-dddddddddddd',
      vd: '2026-10-30',
      doc: 'CC:1094555666',
      slots: 3,
    });

    const revokedSigned = await signTestVoucherJws(privateKey, 'kid-2026-10', {
      jti: 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee',
      bid: 'ffffffff-ffff-4fff-8fff-ffffffffffff',
      sid: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
      xid: 'dddddddd-dddd-4ddd-8ddd-dddddddddddd',
      vd: '2026-10-30',
      doc: 'CC:1094777888',
      slots: 1,
    });

    const fakeHttp = {
      request: vi.fn(() =>
        Promise.resolve({
          rosterDate: '2026-10-30',
          generatedAt: '2026-10-30T09:00:00Z',
          associationId: '55555555-5555-4555-8555-555555555555',
          signingKeys: [
            {
              kid: 'kid-2026-10',
              algorithm: 'RS256',
              publicKeyPem,
              status: 'ACTIVE',
              validFrom: '2026-10-01T00:00:00Z',
            },
          ],
          vouchers: [
            {
              voucherId: '10101010-1010-4010-8010-101010101010',
              jti: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
              bookingId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
              scheduleId: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
              experienceTitle: 'Sendero de Niebla',
              shortCode: 'N8P4Q6R2',
              tokenSha256: validSigned.tokenSha256,
              signingKeyKid: 'kid-2026-10',
              holderName: 'Jorge Eliecer Patiño',
              holderDocument: 'CC 1094555666',
              slotsCount: 3,
              participants: [],
              emergencyContact: { name: 'Elena Patiño', phone: '+573109998877' },
            },
          ],
          revocations: {
            items: [
              {
                voucherId: '20202020-2020-4020-8020-202020202020',
                jti: 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee',
                shortCode: 'X9Y8Z7W6',
                revokedAt: '2026-10-30T08:00:00Z',
                revocationReason: 'BOOKING_CANCELLED',
              },
            ],
            watermark: '2026-10-30T08:00:00Z',
          },
        } satisfies RosterResponseDto),
      ),
    } as unknown as HttpClient;

    await downloadAndStoreRoster(fakeHttp);

    // 1. Token con firma adulterada -> REJECTED_INVALID_SIGNATURE
    const tamperedToken = `${validSigned.token.slice(0, -4)}ABCD`;
    const tamperedRes = await validateAndRedeemQrToken(tamperedToken, 'gestor-1', {
      referenceDate: '2026-10-30',
    });
    expect(tamperedRes.accepted).toBe(false);
    expect(tamperedRes.code).toBe('REJECTED_INVALID_SIGNATURE');
    expect(vibrateSpy).toHaveBeenLastCalledWith([120, 80, 120]);

    // 2. Token revocado -> REJECTED_REVOKED
    const revokedRes = await validateAndRedeemQrToken(revokedSigned.token, 'gestor-1', {
      referenceDate: '2026-10-30',
    });
    expect(revokedRes.accepted).toBe(false);
    expect(revokedRes.code).toBe('REJECTED_REVOKED');

    // 3. Token con fecha equivocada -> REJECTED_WRONG_DATE
    const wrongDateRes = await validateAndRedeemQrToken(validSigned.token, 'gestor-1', {
      referenceDate: '2026-10-31',
    });
    expect(wrongDateRes.accepted).toBe(false);
    expect(wrongDateRes.code).toBe('REJECTED_WRONG_DATE');

    // 4. Doble escaneo concurrente -> exactamente 1 ACCEPTED y 1 REJECTED_ALREADY_CONSUMED (T-2.11, RNF-18)
    const [scanA, scanB] = await Promise.all([
      validateAndRedeemQrToken(validSigned.token, 'gestor-1', { referenceDate: '2026-10-30' }),
      validateAndRedeemQrToken(validSigned.token, 'gestor-1', { referenceDate: '2026-10-30' }),
    ]);

    const acceptedScans = [scanA, scanB].filter((s) => s.accepted);
    const rejectedScans = [scanA, scanB].filter((s) => !s.accepted);
    expect(acceptedScans).toHaveLength(1);
    expect(rejectedScans).toHaveLength(1);
    expect(rejectedScans[0]?.code).toBe('REJECTED_ALREADY_CONSUMED');
    expect(vibrateSpy).toHaveBeenCalledWith(200);
  });

  it('T-2.12: ejecuta contingencia manual por shortCode tanto en línea como sin conexión', async () => {
    const { privateKey, publicKeyPem } = await generateTestRsaKeyPair();
    const signed = await signTestVoucherJws(privateKey, 'kid-2026-10', {
      jti: '12121212-1212-4212-8212-121212121212',
      bid: '34343434-3434-4434-8434-343434343434',
      sid: '56565656-5656-4656-8656-565656565656',
      xid: '78787878-7878-4878-8878-787878787878',
      vd: '2026-10-30',
      doc: 'CC:1094333444',
      slots: 2,
    });

    const rosterDto: RosterResponseDto = {
      rosterDate: '2026-10-30',
      generatedAt: '2026-10-30T09:00:00Z',
      associationId: '55555555-5555-4555-8555-555555555555',
      signingKeys: [
        {
          kid: 'kid-2026-10',
          algorithm: 'RS256',
          publicKeyPem,
          status: 'ACTIVE',
          validFrom: '2026-10-01T00:00:00Z',
        },
      ],
      vouchers: [
        {
          voucherId: '77777777-7777-4777-8777-777777777777',
          jti: '12121212-1212-4212-8212-121212121212',
          bookingId: '34343434-3434-4434-8434-343434343434',
          scheduleId: '56565656-5656-4656-8656-565656565656',
          experienceTitle: 'Taller de Cestería Tradicional',
          shortCode: 'C3D4E5F6',
          tokenSha256: signed.tokenSha256,
          signingKeyKid: 'kid-2026-10',
          holderName: 'Ana Sofía Restrepo',
          holderDocument: 'CC 1094333444',
          slotsCount: 2,
          participants: [],
          emergencyContact: { name: 'Pedro Restrepo', phone: '+573152223344' },
        },
      ],
      revocations: { items: [], watermark: null },
    };

    const offlineHttp = {
      request: vi.fn((path: string) => {
        if (path.startsWith('/v1/offline/roster')) return Promise.resolve(rosterDto);
        return Promise.reject(new NetworkError('sin red'));
      }),
    } as unknown as HttpClient;

    await downloadAndStoreRoster(offlineHttp);

    // Código con formato inválido (contiene '0' o menos de 8 caracteres)
    const invalidFormat = await redeemByManualShortCode({
      shortCode: '012',
      validatedBy: 'gestor-1',
      online: false,
    });
    expect(invalidFormat.code).toBe('REJECTED_INVALID_FORMAT');

    // Si hay un NetworkError intentando en línea, degrada transparentemente al padrón local offline
    const fallbackOffline = await redeemByManualShortCode({
      shortCode: 'c3d4-e5f6',
      validatedBy: 'gestor-1',
      http: offlineHttp,
      online: true,
    });
    expect(fallbackOffline.accepted).toBe(true);
    expect(fallbackOffline.mode).toBe('OFFLINE');
    expect(fallbackOffline.channel).toBe('MANUAL_CODE');

    // Segundo intento del mismo código corto en modo offline -> REJECTED_ALREADY_CONSUMED
    const secondTry = await redeemByManualShortCode({
      shortCode: 'C3D4E5F6',
      validatedBy: 'gestor-1',
      online: false,
    });
    expect(secondTry.accepted).toBe(false);
    expect(secondTry.code).toBe('REJECTED_ALREADY_CONSUMED');
  });

  it('T-2.13: sincroniza lotes idempotentes reutilizando syncBatchId, aplica retroceso exponencial y DEAD_LETTER', async () => {
    const { privateKey, publicKeyPem } = await generateTestRsaKeyPair();
    const signed = await signTestVoucherJws(privateKey, 'kid-2026-10', {
      jti: '90909090-9090-4090-8090-909090909090',
      bid: '80808080-8080-4080-8080-808080808080',
      sid: '70707070-7070-4070-8070-707070707070',
      xid: '60606060-6060-4060-8060-606060606060',
      vd: '2026-10-30',
      doc: 'CC:1094888999',
      slots: 2,
    });

    const rosterDto: RosterResponseDto = {
      rosterDate: '2026-10-30',
      generatedAt: '2026-10-30T09:00:00Z',
      associationId: '55555555-5555-4555-8555-555555555555',
      signingKeys: [
        {
          kid: 'kid-2026-10',
          algorithm: 'RS256',
          publicKeyPem,
          status: 'ACTIVE',
          validFrom: '2026-10-01T00:00:00Z',
        },
      ],
      vouchers: [
        {
          voucherId: '50505050-5050-4050-8050-505050505050',
          jti: '90909090-9090-4090-8090-909090909090',
          bookingId: '80808080-8080-4080-8080-808080808080',
          scheduleId: '70707070-7070-4070-8070-707070707070',
          experienceTitle: 'Avistamiento de Aves del Quindío',
          shortCode: 'G7H8J9K2',
          tokenSha256: signed.tokenSha256,
          signingKeyKid: 'kid-2026-10',
          holderName: 'Camilo Echeverri',
          holderDocument: 'CC 1094888999',
          slotsCount: 2,
          participants: [],
          emergencyContact: { name: 'Laura Echeverri', phone: '+573201112233' },
        },
      ],
      revocations: { items: [], watermark: null },
    };

    let syncAttempt = 0;
    const capturedBatchIds: string[] = [];

    const syncHttp = {
      request: vi.fn((path: string, opts?: { body?: unknown }) => {
        if (path.startsWith('/v1/offline/roster')) return Promise.resolve(rosterDto);
        if (path === '/v1/offline/sync') {
          syncAttempt += 1;
          const payload = opts?.body as {
            syncBatchId: string;
            deviceId: string;
            deviceSentAt: string;
            validations: { localValidationId: string; voucherId: string }[];
          };
          capturedBatchIds.push(payload.syncBatchId);

          if (syncAttempt === 1) {
            return Promise.reject(new NetworkError('sin red'));
          }

          return Promise.resolve({
            syncBatchId: payload.syncBatchId,
            deviceId: payload.deviceId,
            replayed: false,
            deviceSentAt: payload.deviceSentAt,
            serverReceivedAt: '2026-10-30T12:00:05Z',
            processedAt: '2026-10-30T12:00:06Z',
            clockSkewSeconds: 4,
            summary: { received: 1, confirmed: 1, conflicts: 0, rejected: 0 },
            items: payload.validations.map((v) => ({
              localValidationId: v.localValidationId,
              voucherId: v.voucherId,
              status: 'CONFIRMED',
              serverOutcome: 'REDEEMED',
              bookingStatus: 'REDEEMED',
              incidentType: null,
              incidentId: null,
              message: 'Asistencia conciliada exitosamente.',
            })),
          });
        }
        throw new Error(`Unexpected path ${path}`);
      }),
    } as unknown as HttpClient;

    await downloadAndStoreRoster(syncHttp);
    await validateAndRedeemQrToken(signed.token, 'gestor-1', { referenceDate: '2026-10-30' });

    // Intento 1: falla por NetworkError -> se programa FAILED_RETRYABLE con backoff
    const firstAttempt = await flushSyncOutbox(syncHttp, {
      nowMs: 1_000_000,
      randomFn: () => 0.5,
    });
    expect(firstAttempt.retried).toBe(1);
    expect(computeBackoffDelayMs(0, () => 0.5)).toBe(2000);

    // Intento 2 (forzado): reutiliza exactamente el mismo syncBatchId y concilia a SYNCED
    const secondAttempt = await flushSyncOutbox(syncHttp, {
      force: true,
      nowMs: 1_005_000,
    });
    expect(secondAttempt.confirmed).toBe(1);
    expect(capturedBatchIds).toHaveLength(2);
    expect(capturedBatchIds[0]).toBe(capturedBatchIds[1]);
    expect(readOfflineMeta().clockSkewSeconds).toBe(4);

    const db = await getOfflineDB();
    expect(await db.count('sync_outbox')).toBe(0);
    const localValidation = await db.getFromIndex(
      'local_validations',
      'idx_val_voucher_id',
      '90909090-9090-4090-8090-909090909090',
    );
    expect(localValidation?.syncStatus).toBe('SYNCED');
  });

  it('T-2.13: marca en DEAD_LETTER cuando el servidor devuelve un error permanente 422', async () => {
    const db = await getOfflineDB();
    await db.put('operational_roster', {
      voucherId: '30303030-3030-4030-8030-303030303030',
      jti: '40404040-4040-4040-8040-404040404040',
      bookingId: '50505050-5050-4050-8050-505050505050',
      scheduleId: '60606060-6060-4060-8060-606060606060',
      experienceTitle: 'Caminata Nocturna',
      shortCode: 'W2X3Y4Z5',
      tokenSha256: 'a'.repeat(64),
      signingKeyKid: 'kid-1',
      holderName: 'Persona Prueba',
      holderDocument: 'CC 123',
      slotsCount: 1,
      participants: [],
      emergencyContact: { name: 'Contacto', phone: '123' },
      rosterDate: '2026-10-30',
      localStatus: 'ISSUED',
    });

    await redeemByManualShortCode({
      shortCode: 'W2X3Y4Z5',
      validatedBy: 'gestor-1',
      online: false,
    });

    const failingHttp = {
      request: vi.fn(() =>
        Promise.reject(
          new ApiError(
            {
              type: 'about:blank',
              status: 422,
              title: 'Lote inválido',
              detail: 'El formato del lote no pudo procesarse.',
            },
            null,
          ),
        ),
      ),
    } as unknown as HttpClient;

    const res = await flushSyncOutbox(failingHttp, { force: true });
    expect(res.deadLettered).toBe(1);
    const outboxAll = await db.getAll('sync_outbox');
    expect(outboxAll[0]?.status).toBe('DEAD_LETTER');
  });

  it('navega por las vistas de /gestor, /gestor/validar, /gestor/contingencia y /gestor/cola', async () => {
    const { privateKey, publicKeyPem } = await generateTestRsaKeyPair();
    const signed = await signTestVoucherJws(privateKey, 'kid-ui', {
      jti: '13131313-1313-4313-8313-131313131313',
      bid: '24242424-2424-4424-8424-242424242424',
      sid: '35353535-3535-4535-8535-353535353535',
      xid: '46464646-4646-4646-8646-464646464646',
      vd: bogotaDateString(), // sin referenceDate: el motor valida contra HOY en Bogotá
      doc: 'CC:1094123456',
      slots: 2,
    });

    const rosterDto: RosterResponseDto = {
      rosterDate: bogotaDateString(),
      generatedAt: '2026-10-30T08:00:00Z',
      associationId: '55555555-5555-4555-8555-555555555555',
      signingKeys: [
        {
          kid: 'kid-ui',
          algorithm: 'RS256',
          publicKeyPem,
          status: 'ACTIVE',
          validFrom: '2026-10-01T00:00:00Z',
        },
      ],
      vouchers: [
        {
          voucherId: '71717171-7171-4171-8171-717171717171',
          jti: '13131313-1313-4313-8313-131313131313',
          bookingId: '24242424-2424-4424-8424-242424242424',
          scheduleId: '35353535-3535-4535-8535-353535353535',
          experienceTitle: 'Recorrido de Cacao',
          shortCode: 'M2N3P4Q5',
          tokenSha256: signed.tokenSha256,
          signingKeyKid: 'kid-ui',
          holderName: 'Diana Marcela Ríos',
          holderDocument: 'CC 1094123456',
          slotsCount: 2,
          participants: [
            { fullName: 'Diana Marcela Ríos', docType: 'CC', docNumber: '1094123456' },
          ],
          emergencyContact: { name: 'Hernán Ríos', phone: '+573005556677' },
        },
      ],
      revocations: { items: [], watermark: '2026-10-30T08:00:00Z' },
    };

    renderApp(
      '/gestor',
      {
        '/v1/offline/roster': rosterDto,
        '/v1/offline/sync': (_url: URL, opts: { body?: unknown }) => {
          const payload = opts.body as {
            syncBatchId: string;
            deviceId: string;
            deviceSentAt: string;
            validations: { localValidationId: string; voucherId: string }[];
          };
          return {
            syncBatchId: payload.syncBatchId,
            deviceId: payload.deviceId,
            replayed: false,
            deviceSentAt: payload.deviceSentAt,
            serverReceivedAt: '2026-10-30T12:00:00Z',
            processedAt: '2026-10-30T12:00:01Z',
            clockSkewSeconds: 0,
            summary: { received: 1, confirmed: 1, conflicts: 0, rejected: 0 },
            items: payload.validations.map((v) => ({
              localValidationId: v.localValidationId,
              voucherId: v.voucherId,
              status: 'CONFIRMED',
              serverOutcome: 'REDEEMED',
              bookingStatus: 'REDEEMED',
              incidentType: null,
              incidentId: null,
              message: 'Sincronizado en servidor.',
            })),
          };
        },
      },
      { roles: ['ROLE_LINK_MANAGER'] },
    );

    // 1. Descarga el padrón desde /gestor
    await userEvent.click(screen.getByRole('button', { name: 'Descargar padrón de hoy' }));
    expect(await screen.findByText(/Padrón descargado para 2026-10-30/)).toBeVisible();

    // 2. Abre /gestor/validar y verifica el token QR
    await userEvent.click(screen.getByRole('link', { name: /Validar QR en campo/ }));
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Validar QR en campo' }),
    ).toBeVisible();

    const textarea = screen.getByLabelText('Token QR (JWS compacto)');
    await userEvent.type(textarea, signed.token);
    await userEvent.click(
      screen.getByRole('button', { name: 'Verificar firma y registrar ingreso' }),
    );

    expect(await screen.findByRole('heading', { name: 'Ingreso autorizado' })).toBeVisible();
    expect(screen.getAllByText('Diana Marcela Ríos').length).toBeGreaterThanOrEqual(1);

    // 3. Regresa a /gestor y abre /gestor/cola para sincronizar
    await userEvent.click(screen.getByRole('button', { name: 'Atrás' }));
    await userEvent.click(await screen.findByRole('link', { name: /Cola de sincronización/ }));
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Cola de sincronización' }),
    ).toBeVisible();
    expect(await screen.findByText('Pendiente de envío')).toBeVisible();

    const syncBtn = await screen.findByRole('button', { name: 'Sincronizar ahora (1)' });
    await userEvent.click(syncBtn);
    await waitFor(() => {
      expect(screen.getByText('Sincronizado')).toBeVisible();
    });
  });

  describe('validación de fecha y vigencia del QR (ADR-0037 §2.3)', () => {
    async function downloadRosterFor(
      claims: Parameters<typeof signTestVoucherJws>[2],
      rosterDate: string,
    ) {
      const { privateKey, publicKeyPem } = await generateTestRsaKeyPair();
      const signed = await signTestVoucherJws(privateKey, 'kid-fecha', claims);
      const http = {
        request: vi.fn(() =>
          Promise.resolve({
            rosterDate,
            generatedAt: `${rosterDate}T08:00:00Z`,
            associationId: '55555555-5555-4555-8555-555555555555',
            signingKeys: [
              {
                kid: 'kid-fecha',
                algorithm: 'RS256',
                publicKeyPem,
                status: 'ACTIVE',
                validFrom: '2026-10-01T00:00:00Z',
              },
            ],
            vouchers: [
              {
                voucherId: '71717171-7171-4171-8171-717171717171',
                jti: claims.jti,
                bookingId: claims.bid,
                scheduleId: claims.sid,
                experienceTitle: 'Recorrido de Cacao',
                shortCode: 'M2N3P4Q5',
                tokenSha256: signed.tokenSha256,
                signingKeyKid: 'kid-fecha',
                holderName: 'Diana Marcela Ríos',
                holderDocument: 'CC 1094123456',
                slotsCount: claims.slots,
                participants: [],
                emergencyContact: { name: 'Hernán Ríos', phone: '+573005556677' },
              },
            ],
            revocations: { items: [], watermark: null },
          }),
        ),
      } as unknown as HttpClient;
      await downloadAndStoreRoster(http);
      return signed;
    }

    const baseClaims = {
      jti: '99999999-9999-4999-8999-999999999999',
      bid: '88888888-8888-4888-8888-888888888888',
      sid: '77777777-7777-4777-8777-777777777777',
      xid: '66666666-6666-4666-8666-666666666666',
      doc: 'CC:1094123456',
      slots: 1,
    } as const;

    it('compara la fecha del voucher con HOY y no con la fecha del padrón descargado', async () => {
      // Padrón descargado «ayer»: sus vouchers (vd = ayer) ya no valen hoy aunque el padrón siga en el teléfono.
      const yesterday = bogotaDateString(new Date(Date.now() - 86_400_000));
      const signed = await downloadRosterFor({ ...baseClaims, vd: yesterday }, yesterday);

      const result = await validateAndRedeemQrToken(signed.token, 'gestor-1');

      expect(result.accepted).toBe(false);
      expect(result.code).toBe('REJECTED_WRONG_DATE');
      expect(result.message).toContain(`hoy es ${bogotaDateString()}`);
    });

    it('rechaza un voucher con exp vencido aunque su fecha sea la de hoy', async () => {
      const today = bogotaDateString();
      const signed = await downloadRosterFor(
        { ...baseClaims, vd: today, exp: Math.floor(Date.now() / 1000) - 60 },
        today,
      );

      const result = await validateAndRedeemQrToken(signed.token, 'gestor-1');

      expect(result.accepted).toBe(false);
      expect(result.code).toBe('REJECTED_WRONG_DATE');
      expect(result.title).toBe('Voucher vencido');
    });

    it('acepta el voucher de hoy con vigencia y firma correctas sin pasar referenceDate', async () => {
      const today = bogotaDateString();
      const signed = await downloadRosterFor({ ...baseClaims, vd: today }, today);

      const result = await validateAndRedeemQrToken(signed.token, 'gestor-1');

      expect(result.accepted).toBe(true);
    });
  });

  describe('cola de sincronización sin pérdida de asistencias (ADR-0037 §5.3)', () => {
    async function queueOneAttendance(shortCode: string) {
      const db = await getOfflineDB();
      await db.put('operational_roster', {
        voucherId: '31313131-3131-4131-8131-313131313131',
        jti: '41414141-4141-4141-8141-414141414141',
        bookingId: '51515151-5151-4151-8151-515151515151',
        scheduleId: '61616161-6161-4161-8161-616161616161',
        experienceTitle: 'Caminata Nocturna',
        shortCode,
        tokenSha256: 'b'.repeat(64),
        signingKeyKid: 'kid-1',
        holderName: 'Persona Prueba',
        holderDocument: 'CC 123',
        slotsCount: 1,
        participants: [],
        emergencyContact: { name: 'Contacto', phone: '123' },
        rosterDate: bogotaDateString(),
        localStatus: 'ISSUED',
      });
      await redeemByManualShortCode({ shortCode, validatedBy: 'gestor-1', online: false });
      return db;
    }

    function failingWith(error: Error): HttpClient {
      return { request: vi.fn(() => Promise.reject(error)) };
    }

    it('clasifica los fallos: rechazo explícito, sin red y transitorio', () => {
      const problem = (status: number) =>
        new ApiError({ type: 'about:blank', status, title: 't' }, null);

      expect(classifySyncFailure(new NetworkError('sin red'))).toBe('NETWORK');
      for (const status of [400, 403, 409, 422]) {
        expect(classifySyncFailure(problem(status))).toBe('PERMANENT');
      }
      for (const status of [401, 404, 408, 429, 500, 503]) {
        expect(classifySyncFailure(problem(status))).toBe('TRANSIENT');
      }
      expect(classifySyncFailure(new Error('respuesta ilegible'))).toBe('TRANSIENT');
    });

    it('la falta de cobertura nunca agota los reintentos ni manda la asistencia a DEAD_LETTER', async () => {
      const db = await queueOneAttendance('G2H3J4K5');
      const http = failingWith(new NetworkError('sin red'));

      for (let attempt = 0; attempt < 12; attempt++) {
        const result = await flushSyncOutbox(http, { force: true });
        expect(result.deadLettered).toBe(0);
        expect(result.retried).toBe(1);
      }

      const [entry] = await db.getAll('sync_outbox');
      expect(entry?.status).toBe('FAILED_RETRYABLE');
      expect(entry?.retryCount).toBe(0);
    });

    it('un fallo transitorio agota los reintentos pero deja la validación pendiente, no rechazada', async () => {
      const db = await queueOneAttendance('L2M3N4P5');
      const http = failingWith(
        new ApiError({ type: 'about:blank', status: 401, title: 'Sesión' }, null),
      );

      for (let attempt = 0; attempt < SYNC_MAX_RETRIES; attempt++) {
        await flushSyncOutbox(http, { force: true });
      }

      const [entry] = await db.getAll('sync_outbox');
      expect(entry?.status).toBe('DEAD_LETTER');
      const validation = await db.getFromIndex(
        'local_validations',
        'idx_val_voucher_id',
        '41414141-4141-4141-8141-414141414141',
      );
      expect(validation?.syncStatus).toBe('PENDING');
    });

    it('«Reintentar» devuelve los DEAD_LETTER a la cola y se concilian al reenviar', async () => {
      const db = await queueOneAttendance('R2S3T4V5');
      const rejected = failingWith(
        new ApiError({ type: 'about:blank', status: 422, title: 'Lote inválido' }, null),
      );
      await flushSyncOutbox(rejected, { force: true });
      expect((await db.getAll('sync_outbox'))[0]?.status).toBe('DEAD_LETTER');

      expect(await requeueDeadLetters()).toBe(1);

      const [queued] = await db.getAll('sync_outbox');
      expect(queued).toMatchObject({
        status: 'QUEUED',
        retryCount: 0,
        syncBatchId: null,
        lastError: null,
      });
      const accepting = {
        request: vi.fn((_path: string, opts?: { body?: unknown }) => {
          const payload = opts?.body as {
            syncBatchId: string;
            deviceId: string;
            deviceSentAt: string;
            validations: { localValidationId: string; voucherId: string }[];
          };
          return Promise.resolve({
            syncBatchId: payload.syncBatchId,
            deviceId: payload.deviceId,
            replayed: false,
            deviceSentAt: payload.deviceSentAt,
            serverReceivedAt: '2026-10-30T12:00:05Z',
            processedAt: '2026-10-30T12:00:06Z',
            clockSkewSeconds: 0,
            summary: { received: 1, confirmed: 1, conflicts: 0, rejected: 0 },
            items: payload.validations.map((v) => ({
              localValidationId: v.localValidationId,
              voucherId: v.voucherId,
              status: 'CONFIRMED',
              serverOutcome: 'ACCEPTED',
              bookingStatus: 'EXECUTED',
              incidentType: null,
              incidentId: null,
              message: 'Canje exitoso',
            })),
          });
        }),
      } as unknown as HttpClient;
      const result = await flushSyncOutbox(accepting, { force: true });

      expect(result.confirmed).toBe(1);
      expect(await db.count('sync_outbox')).toBe(0);
    });

    it('el retroceso es de ±20 % y respeta el tope de 5 minutos', () => {
      expect(computeBackoffDelayMs(0, () => 0)).toBe(1600);
      expect(computeBackoffDelayMs(0, () => 0.5)).toBe(2000);
      expect(computeBackoffDelayMs(0, () => 1)).toBe(2400);
      // Con muchos reintentos la base se queda en 300 000 ms: nunca pasa de 360 000 ms (+20 %).
      expect(computeBackoffDelayMs(20, () => 1)).toBe(360_000);
      expect(computeBackoffDelayMs(20, () => 0)).toBe(240_000);
    });
  });
});
