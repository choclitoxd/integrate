import { INACTIVITY_PURGE_MS, type OfflineDeviceMeta, type RosterDownloadMeta } from './types';

export const OFFLINE_META_STORAGE_KEY = 'integrate.offline.meta.v1' as const;

const UUID_V4_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function generateUuidV4(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  bytes[6] = ((bytes[6] ?? 0) & 0x0f) | 0x40;
  bytes[8] = ((bytes[8] ?? 0) & 0x3f) | 0x80;
  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

function getStorage(storage?: Storage): Storage | null {
  if (storage) return storage;
  try {
    return typeof localStorage !== 'undefined' ? localStorage : null;
  } catch {
    return null;
  }
}

function createDefaultMeta(nowIso: string): OfflineDeviceMeta {
  return {
    deviceId: generateUuidV4(),
    rosterMeta: null,
    catalogUpdatedAt: null,
    clockSkewSeconds: 0,
    lastActivityAt: nowIso,
    nextSequenceNumber: 1,
  };
}

function isValidRosterMeta(raw: unknown): raw is RosterDownloadMeta {
  if (typeof raw !== 'object' || raw === null) return false;
  const candidate = raw as Record<string, unknown>;
  return (
    typeof candidate.rosterDate === 'string' &&
    typeof candidate.generatedAt === 'string' &&
    typeof candidate.associationId === 'string' &&
    (candidate.revocationsWatermark === null ||
      typeof candidate.revocationsWatermark === 'string') &&
    typeof candidate.voucherCount === 'number'
  );
}

/** Lee los metadatos operativos locales (creando e inicializando `deviceId` si aún no existe). */
export function readOfflineMeta(
  options: { readonly storage?: Storage; readonly now?: () => Date } = {},
): OfflineDeviceMeta {
  const nowIso = (options.now?.() ?? new Date()).toISOString();
  const target = getStorage(options.storage);
  if (!target) return createDefaultMeta(nowIso);

  const raw = target.getItem(OFFLINE_META_STORAGE_KEY);
  if (raw) {
    try {
      const parsed = JSON.parse(raw) as Record<string, unknown>;
      const deviceId =
        typeof parsed.deviceId === 'string' && UUID_V4_PATTERN.test(parsed.deviceId)
          ? parsed.deviceId
          : generateUuidV4();
      const meta: OfflineDeviceMeta = {
        deviceId,
        rosterMeta: isValidRosterMeta(parsed.rosterMeta) ? parsed.rosterMeta : null,
        catalogUpdatedAt:
          typeof parsed.catalogUpdatedAt === 'string' ? parsed.catalogUpdatedAt : null,
        clockSkewSeconds:
          typeof parsed.clockSkewSeconds === 'number' && Number.isFinite(parsed.clockSkewSeconds)
            ? parsed.clockSkewSeconds
            : 0,
        lastActivityAt: typeof parsed.lastActivityAt === 'string' ? parsed.lastActivityAt : nowIso,
        nextSequenceNumber:
          typeof parsed.nextSequenceNumber === 'number' &&
          Number.isInteger(parsed.nextSequenceNumber) &&
          parsed.nextSequenceNumber >= 1
            ? parsed.nextSequenceNumber
            : 1,
        ...(typeof parsed.lastSyncedAt === 'string' && { lastSyncedAt: parsed.lastSyncedAt }),
      };
      if (deviceId !== parsed.deviceId) {
        target.setItem(OFFLINE_META_STORAGE_KEY, JSON.stringify(meta));
      }
      return meta;
    } catch {
      // Si el JSON se corrompió, regenera metadatos limpios.
    }
  }

  const fresh = createDefaultMeta(nowIso);
  try {
    target.setItem(OFFLINE_META_STORAGE_KEY, JSON.stringify(fresh));
  } catch {
    // Cuota de localStorage agotada o modo privado restringido.
  }
  return fresh;
}

/** Devuelve el identificador único de este terminal (`X-Device-Id`, ADR-0032 §1). */
export function getOrCreateDeviceId(options: { readonly storage?: Storage } = {}): string {
  return readOfflineMeta(options).deviceId;
}

/** Actualiza parcialmente los metadatos operativos conservando el `deviceId` inmutable. */
export function updateOfflineMeta(
  patch: Partial<Omit<OfflineDeviceMeta, 'deviceId'>>,
  options: { readonly storage?: Storage; readonly now?: () => Date } = {},
): OfflineDeviceMeta {
  const current = readOfflineMeta(options);
  const next: OfflineDeviceMeta = {
    ...current,
    ...patch,
    deviceId: current.deviceId,
  };
  const target = getStorage(options.storage);
  try {
    target?.setItem(OFFLINE_META_STORAGE_KEY, JSON.stringify(next));
  } catch {
    // Ignorado si localStorage no permite escritura.
  }
  return next;
}

/** Reserva el siguiente `sequenceNumber` monotónico para `sync_outbox` (`idx_outbox_order`). */
export function allocateSequenceNumber(
  options: { readonly storage?: Storage; readonly now?: () => Date } = {},
): number {
  const current = readOfflineMeta(options);
  const seq = current.nextSequenceNumber;
  updateOfflineMeta(
    {
      nextSequenceNumber: seq + 1,
      lastActivityAt: (options.now?.() ?? new Date()).toISOString(),
    },
    options,
  );
  return seq;
}

/** Registra actividad reciente del Gestor en el dispositivo. */
export function touchOfflineActivity(
  options: { readonly storage?: Storage; readonly now?: () => Date } = {},
): OfflineDeviceMeta {
  const nowIso = (options.now?.() ?? new Date()).toISOString();
  return updateOfflineMeta({ lastActivityAt: nowIso }, options);
}

/** Evalúa si han transcurrido más de 7 días continuos sin actividad (`offline_data_indexeddb.md` §4.2.2). */
export function isInactiveBeyondRetention(
  meta: Pick<OfflineDeviceMeta, 'lastActivityAt'>,
  now: Date = new Date(),
): boolean {
  const lastMs = Date.parse(meta.lastActivityAt);
  if (Number.isNaN(lastMs)) return false;
  return now.getTime() - lastMs > INACTIVITY_PURGE_MS;
}

/**Fecha calendario actual en `America/Bogota` (`YYYY-MM-DD`). */
export function bogotaDateString(date: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Bogota',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(date);
}
