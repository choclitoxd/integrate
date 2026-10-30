import { z } from 'zod/mini';

const jwsHeaderSchema = z.object({
  alg: z.string().check(z.minLength(1)),
  typ: z.optional(z.string()),
  kid: z.string().check(z.minLength(1)),
});

const jwsPayloadSchema = z.object({
  iss: z.optional(z.string()),
  jti: z.string().check(z.minLength(1)),
  bid: z.string().check(z.minLength(1)),
  sid: z.string().check(z.minLength(1)),
  xid: z.string().check(z.minLength(1)),
  vd: z.string().check(z.regex(/^\d{4}-\d{2}-\d{2}$/)),
  doc: z.string().check(z.minLength(1)),
  slots: z.int().check(z.positive()),
  iat: z.int(),
  exp: z.int(),
});

export type VoucherJwsHeader = z.infer<typeof jwsHeaderSchema>;
export type VoucherJwsPayload = z.infer<typeof jwsPayloadSchema>;

export interface ParsedVoucherJws {
  readonly rawToken: string;
  readonly header: VoucherJwsHeader;
  readonly payload: VoucherJwsPayload;
  readonly signingInputBytes: Uint8Array<ArrayBuffer>;
  readonly signatureBytes: Uint8Array<ArrayBuffer>;
}

/** Convierte una cadena Base64URL (RFC 7515) a `Uint8Array<ArrayBuffer>`. */
export function base64UrlToBytes(b64url: string): Uint8Array<ArrayBuffer> {
  const normalized = b64url.replace(/-/g, '+').replace(/_/g, '/');
  const padLen = (4 - (normalized.length % 4)) % 4;
  const padded = normalized + '='.repeat(padLen);
  const binary = atob(padded);
  const buffer = new ArrayBuffer(binary.length);
  const bytes = new Uint8Array(buffer);
  for (let i = 0; i < binary.length; i += 1) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

/** Codifica `Uint8Array` a cadena Base64URL sin relleno `=`. */
export function bytesToBase64Url(bytes: Uint8Array): string {
  let binary = '';
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function encodeAsciiBytes(text: string): Uint8Array<ArrayBuffer> {
  const encoded = new TextEncoder().encode(text);
  const buffer = new ArrayBuffer(encoded.byteLength);
  const copy = new Uint8Array(buffer);
  copy.set(encoded);
  return copy;
}

/** Calcula el resumen SHA-256 en hexadecimal minúscula (`64` caracteres) usando Web Crypto API. */
export async function sha256Hex(input: string | Uint8Array<ArrayBuffer>): Promise<string> {
  const data = typeof input === 'string' ? encodeAsciiBytes(input) : input;
  const digestBuffer = await crypto.subtle.digest('SHA-256', data);
  const digestBytes = new Uint8Array(digestBuffer);
  let hex = '';
  for (const byte of digestBytes) {
    hex += byte.toString(16).padStart(2, '0');
  }
  return hex;
}

/**
 * Parsea y valida estructuralmente un token JWS compacto (`headerB64Url.payloadB64Url.signatureB64Url`)
 * conforme a ADR-0010 §2 y ADR-0037 §2.
 */
export function parseCompactVoucherJws(rawInput: string): ParsedVoucherJws {
  const trimmed = rawInput.trim();
  const parts = trimmed.split('.');
  if (parts.length !== 3) {
    throw new Error('INVALID_JWS_FORMAT');
  }
  const [headerB64, payloadB64, signatureB64] = parts as [string, string, string];
  if (!headerB64 || !payloadB64 || !signatureB64) {
    throw new Error('INVALID_JWS_FORMAT');
  }

  let headerJson: unknown;
  let payloadJson: unknown;
  let signatureBytes: Uint8Array<ArrayBuffer>;
  try {
    const decoder = new TextDecoder();
    headerJson = JSON.parse(decoder.decode(base64UrlToBytes(headerB64)));
    payloadJson = JSON.parse(decoder.decode(base64UrlToBytes(payloadB64)));
    signatureBytes = base64UrlToBytes(signatureB64);
  } catch {
    throw new Error('INVALID_JWS_ENCODING');
  }

  const headerParsed = jwsHeaderSchema.safeParse(headerJson);
  const payloadParsed = jwsPayloadSchema.safeParse(payloadJson);
  if (!headerParsed.success || !payloadParsed.success) {
    throw new Error('INVALID_JWS_CLAIMS');
  }

  return {
    rawToken: trimmed,
    header: headerParsed.data,
    payload: payloadParsed.data,
    signingInputBytes: encodeAsciiBytes(`${headerB64}.${payloadB64}`),
    signatureBytes,
  };
}

/** Extrae el bloque DER (`ArrayBuffer`) de una clave pública en formato PEM (`PUBLIC KEY`). */
export function spkiPemToArrayBuffer(pem: string): ArrayBuffer {
  const base64Body = pem
    .replace(/-----BEGIN PUBLIC KEY-----/g, '')
    .replace(/-----END PUBLIC KEY-----/g, '')
    .replace(/\s+/g, '');
  if (!base64Body) {
    throw new Error('INVALID_PUBLIC_KEY_PEM');
  }
  const binary = atob(base64Body);
  const buffer = new ArrayBuffer(binary.length);
  const bytes = new Uint8Array(buffer);
  for (let i = 0; i < binary.length; i += 1) {
    bytes[i] = binary.charCodeAt(i);
  }
  return buffer;
}

const importedKeyCache = new Map<string, CryptoKey>();

/** Limpia la caché en memoria de llaves criptográficas importadas (útil en pruebas). */
export function clearCryptoKeyCache(): void {
  importedKeyCache.clear();
}

async function importPublicVerifyKey(
  kid: string,
  algorithm: 'RS256' | 'Ed25519',
  publicKeyPem: string,
): Promise<CryptoKey> {
  const cacheKey = `${kid}:${algorithm}:${String(publicKeyPem.length)}`;
  const cached = importedKeyCache.get(cacheKey);
  if (cached) return cached;

  const spkiBuffer = spkiPemToArrayBuffer(publicKeyPem);
  const key =
    algorithm === 'RS256'
      ? await crypto.subtle.importKey(
          'spki',
          spkiBuffer,
          { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
          false,
          ['verify'],
        )
      : await crypto.subtle.importKey('spki', spkiBuffer, { name: 'Ed25519' }, false, ['verify']);

  importedKeyCache.set(cacheKey, key);
  return key;
}

/**
 * Verifica criptográficamente la firma digital del JWS compacto contra la clave pública SPKI
 * almacenada en `public_signing_keys` usando Web Crypto API (`crypto.subtle.verify`, ADR-0037 §2.2, RNF-05).
 */
export async function verifyVoucherJwsSignature(
  parsed: ParsedVoucherJws,
  signingKey: {
    readonly kid: string;
    readonly algorithm: 'RS256' | 'Ed25519';
    readonly publicKeyPem: string;
  },
): Promise<{
  readonly valid: boolean;
  readonly tokenSha256: string;
  readonly signatureSha256: string;
}> {
  if (parsed.header.alg !== signingKey.algorithm) {
    return {
      valid: false,
      tokenSha256: await sha256Hex(parsed.rawToken),
      signatureSha256: await sha256Hex(parsed.signatureBytes),
    };
  }

  try {
    const cryptoKey = await importPublicVerifyKey(
      signingKey.kid,
      signingKey.algorithm,
      signingKey.publicKeyPem,
    );
    const verifyAlgorithm =
      signingKey.algorithm === 'RS256' ? { name: 'RSASSA-PKCS1-v1_5' } : { name: 'Ed25519' };

    const valid = await crypto.subtle.verify(
      verifyAlgorithm,
      cryptoKey,
      parsed.signatureBytes,
      parsed.signingInputBytes,
    );
    const [tokenSha256, signatureSha256] = await Promise.all([
      sha256Hex(parsed.rawToken),
      sha256Hex(parsed.signatureBytes),
    ]);
    return { valid, tokenSha256, signatureSha256 };
  } catch {
    return {
      valid: false,
      tokenSha256: await sha256Hex(parsed.rawToken),
      signatureSha256: await sha256Hex(parsed.signatureBytes),
    };
  }
}
