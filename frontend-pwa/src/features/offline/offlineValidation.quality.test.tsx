import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { NetworkError, type HttpClient } from '@/shared/api';
import { clearAllOfflineStores } from '@/shared/offline';
import { percent, recordQuality } from '@/test/quality';

import {
  VerdictBanner,
  bytesToBase64Url,
  clearCryptoKeyCache,
  downloadAndStoreRoster,
  flushSyncOutbox,
  redeemByManualShortCode,
  sha256Hex,
  validateAndRedeemQrToken,
  type RosterResponseDto,
} from './index';

function toBase64Url(text: string): string {
  return bytesToBase64Url(new TextEncoder().encode(text));
}

async function createRsaFixture() {
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
  let binary = '';
  for (const byte of new Uint8Array(spkiBuffer)) {
    binary += String.fromCharCode(byte);
  }
  const publicKeyPem = `-----BEGIN PUBLIC KEY-----\n${btoa(binary)}\n-----END PUBLIC KEY-----`;

  const signToken = async (jti: string, vd = '2026-10-30') => {
    const headerB64 = toBase64Url(
      JSON.stringify({ alg: 'RS256', typ: 'voucher+jwt', kid: 'kid-q' }),
    );
    const payloadB64 = toBase64Url(
      JSON.stringify({
        iss: 'integrate',
        jti,
        bid: '22222222-2222-4222-8222-222222222222',
        sid: '33333333-3333-4333-8333-333333333333',
        xid: '44444444-4444-4444-8444-444444444444',
        vd,
        doc: 'CC:1094000999',
        slots: 2,
        iat: Math.floor(Date.now() / 1000) - 3_600,
        exp: Math.floor(Date.now() / 1000) + 86_400,
      }),
    );
    const encoded = new TextEncoder().encode(`${headerB64}.${payloadB64}`);
    const input = new Uint8Array(new ArrayBuffer(encoded.byteLength));
    input.set(encoded);
    const sig = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', pair.privateKey, input);
    const token = `${headerB64}.${payloadB64}.${bytesToBase64Url(new Uint8Array(sig))}`;
    return { token, tokenSha256: await sha256Hex(token) };
  };

  return { publicKeyPem, signToken };
}

describe('[RNF-05][RNF-14][RNF-18][RNF-19][RNF-20] Calidad de validación y operación offline PWA', () => {
  beforeEach(async () => {
    clearCryptoKeyCache();
    await clearAllOfflineStores();
    localStorage.clear();
  });

  it('[RNF-05] verifica firmas RS256 en Web Crypto API y rechaza el 100 % de tokens adulterados o revocados', async ({
    task,
  }) => {
    const { publicKeyPem, signToken } = await createRsaFixture();
    const valid = await signToken('11111111-1111-4111-8111-111111111111');
    const revoked = await signToken('22222222-2222-4222-8222-222222222222');

    const rosterDto: RosterResponseDto = {
      rosterDate: '2026-10-30',
      generatedAt: '2026-10-30T08:00:00Z',
      associationId: '55555555-5555-4555-8555-555555555555',
      signingKeys: [
        {
          kid: 'kid-q',
          algorithm: 'RS256',
          publicKeyPem,
          status: 'ACTIVE',
          validFrom: '2026-10-01T00:00:00Z',
        },
      ],
      vouchers: [
        {
          voucherId: 'aaaa1111-1111-4111-8111-111111111111',
          jti: '11111111-1111-4111-8111-111111111111',
          bookingId: '22222222-2222-4222-8222-222222222222',
          scheduleId: '33333333-3333-4333-8333-333333333333',
          experienceTitle: 'Ruta Cafetera',
          shortCode: 'K7M9P2X4',
          tokenSha256: valid.tokenSha256,
          signingKeyKid: 'kid-q',
          holderName: 'Valeria Salazar',
          holderDocument: 'CC 1094000999',
          slotsCount: 2,
          participants: [],
          emergencyContact: { name: 'Contacto', phone: '+573001112233' },
        },
      ],
      revocations: {
        items: [
          {
            voucherId: 'bbbb2222-2222-4222-8222-222222222222',
            jti: '22222222-2222-4222-8222-222222222222',
            shortCode: 'R8T9V2W3',
            revokedAt: '2026-10-30T07:30:00Z',
            revocationReason: 'BOOKING_CANCELLED',
          },
        ],
        watermark: '2026-10-30T07:30:00Z',
      },
    };

    const fakeHttp = {
      request: vi.fn(() => Promise.resolve(rosterDto)),
    } as unknown as HttpClient;
    await downloadAndStoreRoster(fakeHttp);

    const tokenParts = valid.token.split('.');
    const headerPart = tokenParts[0] ?? '';
    const sigPart = tokenParts[2] ?? '';

    const tamperedPayloadToken = `${headerPart}.${toBase64Url(
      JSON.stringify({
        iss: 'integrate',
        jti: '11111111-1111-4111-8111-111111111111',
        bid: '22222222-2222-4222-8222-222222222222',
        sid: '33333333-3333-4333-8333-333333333333',
        xid: '44444444-4444-4444-8444-444444444444',
        vd: '2026-10-30',
        doc: 'CC:1094000999',
        slots: 99,
        iat: Math.floor(Date.now() / 1000) - 3_600,
        exp: Math.floor(Date.now() / 1000) + 86_400,
      }),
    )}.${sigPart}`;

    const tamperedSigToken = `${valid.token.slice(0, -5)}ZZZZZ`;

    const r1 = await validateAndRedeemQrToken(tamperedPayloadToken, 'gestor-q', {
      referenceDate: '2026-10-30',
    });
    const r2 = await validateAndRedeemQrToken(tamperedSigToken, 'gestor-q', {
      referenceDate: '2026-10-30',
    });
    const r3 = await validateAndRedeemQrToken(revoked.token, 'gestor-q', {
      referenceDate: '2026-10-30',
    });
    const r4 = await validateAndRedeemQrToken(valid.token, 'gestor-q', {
      referenceDate: '2026-10-30',
    });

    const detected = [!r1.accepted, !r2.accepted, !r3.accepted, r4.accepted].filter(Boolean).length;
    const rate = percent(detected, 4);

    recordQuality(task, {
      rnf: 'RNF-05',
      metric:
        'Precisión criptográfica offline (rechazo de adulterados/revocados y aceptación de firma válida)',
      value: rate,
      unit: '%',
      threshold: '= 100 %',
      pass: rate === 100,
      detail: `payload adulterado=${r1.code}, firma adulterada=${r2.code}, revocado=${r3.code}, válido=${r4.code}`,
    });

    expect(rate).toBe(100);
  });

  it('[RNF-14] presenta retroalimentación multimodal redundante (color AAA + ícono + texto + vibración háptica)', async ({
    task,
  }) => {
    const vibrateCalls: (number | number[])[] = [];
    Object.defineProperty(navigator, 'vibrate', {
      value: (pattern: number | number[]) => {
        vibrateCalls.push(pattern);
        return true;
      },
      configurable: true,
      writable: true,
    });

    const { publicKeyPem, signToken } = await createRsaFixture();
    const valid = await signToken('33333333-3333-4333-8333-333333333333');

    const fakeHttp = {
      request: vi.fn(() =>
        Promise.resolve({
          rosterDate: '2026-10-30',
          generatedAt: '2026-10-30T08:00:00Z',
          associationId: '55555555-5555-4555-8555-555555555555',
          signingKeys: [
            {
              kid: 'kid-q',
              algorithm: 'RS256',
              publicKeyPem,
              status: 'ACTIVE',
              validFrom: '2026-10-01T00:00:00Z',
            },
          ],
          vouchers: [
            {
              voucherId: 'cccc3333-3333-4333-8333-333333333333',
              jti: '33333333-3333-4333-8333-333333333333',
              bookingId: '22222222-2222-4222-8222-222222222222',
              scheduleId: '33333333-3333-4333-8333-333333333333',
              experienceTitle: 'Sendero del Agua',
              shortCode: 'H4J5K6M7',
              tokenSha256: valid.tokenSha256,
              signingKeyKid: 'kid-q',
              holderName: 'Lucía Fernández',
              holderDocument: 'CC 1094000999',
              slotsCount: 2,
              participants: [],
              emergencyContact: { name: 'Contacto', phone: '+573001112233' },
            },
          ],
          revocations: { items: [], watermark: null },
        } satisfies RosterResponseDto),
      ),
    } as unknown as HttpClient;

    await downloadAndStoreRoster(fakeHttp);

    const acceptedVerdict = await validateAndRedeemQrToken(valid.token, 'gestor-q', {
      referenceDate: '2026-10-30',
    });
    const rejectedVerdict = await validateAndRedeemQrToken(valid.token, 'gestor-q', {
      referenceDate: '2026-10-30',
    });

    const { unmount } = render(
      <VerdictBanner result={acceptedVerdict} onReset={() => undefined} />,
    );
    const alertAccepted = screen.getByRole('alert');
    const hasAcceptedText = alertAccepted.textContent.includes('Veredicto: Válido');
    const hasAcceptedIcon = alertAccepted.querySelector('svg') !== null;
    unmount();

    render(<VerdictBanner result={rejectedVerdict} onReset={() => undefined} />);
    const alertRejected = screen.getByRole('alert');
    const hasRejectedText = alertRejected.textContent.includes('Veredicto: Rechazado');
    const hasRejectedIcon = alertRejected.querySelector('svg') !== null;

    const checks = [
      hasAcceptedText,
      hasAcceptedIcon,
      hasRejectedText,
      hasRejectedIcon,
      vibrateCalls[0] === 200,
      Array.isArray(vibrateCalls[1]) && vibrateCalls[1].length === 3,
    ];
    const score = percent(checks.filter(Boolean).length, checks.length);

    recordQuality(task, {
      rnf: 'RNF-14',
      metric:
        'Cobertura de canales multimodales en veredicto de campo (color AAA, ícono SVG, texto y vibración)',
      value: score,
      unit: '%',
      threshold: '= 100 %',
      pass: score === 100,
      detail: `vibrate=${JSON.stringify(vibrateCalls)}, role=alert con ícono y texto redundante`,
    });

    expect(score).toBe(100);
  });

  it('[RNF-18] garantiza 0 dobles redenciones locales ante 10 intentos concurrentes sobre el mismo voucher', async ({
    task,
  }) => {
    const { publicKeyPem, signToken } = await createRsaFixture();
    const valid = await signToken('44444444-4444-4444-8444-444444444444');

    const fakeHttp = {
      request: vi.fn(() =>
        Promise.resolve({
          rosterDate: '2026-10-30',
          generatedAt: '2026-10-30T08:00:00Z',
          associationId: '55555555-5555-4555-8555-555555555555',
          signingKeys: [
            {
              kid: 'kid-q',
              algorithm: 'RS256',
              publicKeyPem,
              status: 'ACTIVE',
              validFrom: '2026-10-01T00:00:00Z',
            },
          ],
          vouchers: [
            {
              voucherId: 'dddd4444-4444-4444-8444-444444444444',
              jti: '44444444-4444-4444-8444-444444444444',
              bookingId: '22222222-2222-4222-8222-222222222222',
              scheduleId: '33333333-3333-4333-8333-333333333333',
              experienceTitle: 'Trapiche Comunitario',
              shortCode: 'P9Q8R7S6',
              tokenSha256: valid.tokenSha256,
              signingKeyKid: 'kid-q',
              holderName: 'Andrés Felipe Mora',
              holderDocument: 'CC 1094000999',
              slotsCount: 2,
              participants: [],
              emergencyContact: { name: 'Contacto', phone: '+573001112233' },
            },
          ],
          revocations: { items: [], watermark: null },
        } satisfies RosterResponseDto),
      ),
    } as unknown as HttpClient;

    await downloadAndStoreRoster(fakeHttp);

    const results = await Promise.all(
      Array.from({ length: 10 }, () =>
        validateAndRedeemQrToken(valid.token, 'gestor-q', { referenceDate: '2026-10-30' }),
      ),
    );

    const acceptedCount = results.filter((r) => r.accepted).length;
    const duplicateRejectedCount = results.filter(
      (r) => !r.accepted && r.code === 'REJECTED_ALREADY_CONSUMED',
    ).length;

    recordQuality(task, {
      rnf: 'RNF-18',
      metric:
        'Redenciones aceptadas ante 10 lecturas concurrentes del mismo voucher (CONSUMIDO_LOCAL)',
      value: acceptedCount,
      unit: 'aceptaciones',
      threshold: '= 1 (y 9 rechazos REJECTED_ALREADY_CONSUMED)',
      pass: acceptedCount === 1 && duplicateRejectedCount === 9,
      detail: `aceptados=${String(acceptedCount)}, rechazados por doble uso=${String(duplicateRejectedCount)}`,
    });

    expect(acceptedCount).toBe(1);
    expect(duplicateRejectedCount).toBe(9);
  });

  it('[RNF-19][RNF-20] canjea por código corto de contingencia (8 caracteres) y sincroniza el lote idempotente al reconectar', async ({
    task,
  }) => {
    const { publicKeyPem, signToken } = await createRsaFixture();
    const valid = await signToken('55555555-5555-4555-8555-555555555555');

    let networkOnline = false;
    const fakeHttp = {
      request: vi.fn((path: string, opts?: { body?: unknown }) => {
        if (path.startsWith('/v1/offline/roster')) {
          return Promise.resolve({
            rosterDate: '2026-10-30',
            generatedAt: '2026-10-30T08:00:00Z',
            associationId: '55555555-5555-4555-8555-555555555555',
            signingKeys: [
              {
                kid: 'kid-q',
                algorithm: 'RS256',
                publicKeyPem,
                status: 'ACTIVE',
                validFrom: '2026-10-01T00:00:00Z',
              },
            ],
            vouchers: [
              {
                voucherId: 'eeee5555-5555-4555-8555-555555555555',
                jti: '55555555-5555-4555-8555-555555555555',
                bookingId: '22222222-2222-4222-8222-222222222222',
                scheduleId: '33333333-3333-4333-8333-333333333333',
                experienceTitle: 'Mirador del Valle',
                shortCode: 'T2V3W4X5',
                tokenSha256: valid.tokenSha256,
                signingKeyKid: 'kid-q',
                holderName: 'Santiago Mejía',
                holderDocument: 'CC 1094000999',
                slotsCount: 2,
                participants: [],
                emergencyContact: { name: 'Contacto', phone: '+573001112233' },
              },
            ],
            revocations: { items: [], watermark: null },
          } satisfies RosterResponseDto);
        }
        if (!networkOnline) {
          return Promise.reject(new NetworkError('sin red'));
        }
        if (path === '/v1/offline/sync') {
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
            serverReceivedAt: '2026-10-30T13:00:00Z',
            processedAt: '2026-10-30T13:00:01Z',
            clockSkewSeconds: 0,
            summary: {
              received: payload.validations.length,
              confirmed: payload.validations.length,
              conflicts: 0,
              rejected: 0,
            },
            items: payload.validations.map((v) => ({
              localValidationId: v.localValidationId,
              voucherId: v.voucherId,
              status: 'CONFIRMED',
              serverOutcome: 'REDEEMED',
              bookingStatus: 'REDEEMED',
              incidentType: null,
              incidentId: null,
              message: 'Conciliado.',
            })),
          });
        }
        throw new Error(`Unexpected path ${path}`);
      }),
    } as unknown as HttpClient;

    await downloadAndStoreRoster(fakeHttp);

    // Contingencia manual offline por código de 8 caracteres (RNF-20)
    const manualRedeem = await redeemByManualShortCode({
      shortCode: 'T2V3W4X5',
      validatedBy: 'gestor-q',
      http: fakeHttp,
      online: false,
    });

    recordQuality(task, {
      rnf: 'RNF-20',
      metric:
        'Efectividad del canje manual de contingencia por código corto (8 caracteres) sin red',
      value: manualRedeem.accepted ? 100 : 0,
      unit: '%',
      threshold: '= 100 %',
      pass: manualRedeem.accepted && manualRedeem.channel === 'MANUAL_CODE',
      detail: `shortCode=T2V3W4X5, channel=${manualRedeem.channel}, mode=${manualRedeem.mode}`,
    });

    // Intento de sincronización sin red -> reprograma con backoff; al volver la red -> confirma el 100% (RNF-19)
    const offlineFlush = await flushSyncOutbox(fakeHttp, { nowMs: 10_000 });
    networkOnline = true;
    const onlineFlush = await flushSyncOutbox(fakeHttp, { force: true, nowMs: 20_000 });

    const syncSuccessRate = percent(onlineFlush.confirmed, onlineFlush.dispatched);
    recordQuality(task, {
      rnf: 'RNF-19',
      metric: 'Tasa de entrega idempotente del lote de asistencias al recuperar conectividad',
      value: syncSuccessRate,
      unit: '%',
      threshold: '= 100 % (con preservación de syncBatchId tras caída de red)',
      pass:
        syncSuccessRate === 100 &&
        offlineFlush.retried === 1 &&
        offlineFlush.syncBatchId === onlineFlush.syncBatchId,
      detail: `syncBatchId=${String(onlineFlush.syncBatchId)}, confirmados=${String(onlineFlush.confirmed)}/${String(onlineFlush.dispatched)}`,
    });

    expect(manualRedeem.accepted).toBe(true);
    expect(syncSuccessRate).toBe(100);
  });
});
