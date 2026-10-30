/**
 * Tipos de dominio para los 6 Object Stores de `IntegrateOfflineDB` (v1) y metadatos operativos.
 * Trazabilidad: `docs/architecture/offline_data_indexeddb.md` §2–§4, ADR-0032, ADR-0033 y ADR-0036.
 */

export const OFFLINE_DB_NAME = 'IntegrateOfflineDB' as const;
export const OFFLINE_DB_VERSION = 1 as const;

/** Presupuesto duro de almacenamiento offline en smartphones rurales (< 10 MB, RNF-17 / T-2.8). */
export const MAX_OFFLINE_BYTES = 10 * 1024 * 1024;
/** Umbral preventivo de alerta de cuota (80 % = 8 MB). */
export const WARN_OFFLINE_BYTES = 8 * 1024 * 1024;
/** TTL del catálogo local en milisegundos (72 horas, `offline_data_indexeddb.md` §4.2.1). */
export const CATALOG_TTL_MS = 72 * 60 * 60 * 1000;
/** TTL del padrón operativo en milisegundos (24 horas, `offline_data_indexeddb.md` §4.2.1). */
export const ROSTER_TTL_MS = 24 * 60 * 60 * 1000;
/** Ventana máxima de inactividad antes de purga automática (7 días, `offline_data_indexeddb.md` §4.2.2). */
export const INACTIVITY_PURGE_MS = 7 * 24 * 60 * 60 * 1000;

export const OBJECT_STORE_NAMES = [
  'read_only_catalog',
  'operational_roster',
  'revocation_list',
  'public_signing_keys',
  'local_validations',
  'sync_outbox',
] as const;

export type ObjectStoreName = (typeof OBJECT_STORE_NAMES)[number];

/** 1. Object Store: `read_only_catalog` (`keyPath: 'id'`). */
export interface OfflineCatalogSchedule {
  readonly scheduleId: string;
  readonly startTime: string;
  readonly endTime: string;
  readonly status: string;
}

export interface OfflineCatalogEntry {
  readonly id: string;
  readonly hostId: string;
  readonly hostName: string;
  readonly title: string;
  readonly description: string;
  readonly safetyGuidelines: string;
  readonly physicalIntensity: 'BAJA' | 'MEDIA' | 'ALTA';
  readonly durationMinutes: number;
  readonly municipalityCode: string;
  readonly municipalityName: string;
  readonly vereda: string;
  readonly meetingPoint: string;
  readonly coordinates: {
    readonly latitude: number;
    readonly longitude: number;
  };
  readonly maxCapacityPerSlot: number;
  readonly unitPriceCop: number;
  readonly coverImageUrl: string;
  readonly categories: readonly string[];
  readonly schedules: readonly OfflineCatalogSchedule[];
}

/** 2. Object Store: `operational_roster` (`keyPath: 'voucherId'`). Nunca incluye `eps_enc`. */
export interface OfflineRosterParticipant {
  readonly fullName: string;
  readonly docType: string;
  readonly docNumber: string;
}

export interface OfflineEmergencyContact {
  readonly name: string;
  readonly phone: string;
}

export interface OfflineRosterEntry {
  readonly voucherId: string;
  /** Claim `jti` del JWT del voucher (ADR-0033 §1). Indexado de forma única en `idx_roster_jti`. */
  readonly jti: string;
  readonly bookingId: string;
  readonly scheduleId: string;
  readonly experienceTitle: string;
  readonly shortCode: string;
  readonly tokenSha256: string;
  readonly signingKeyKid: string;
  readonly holderName: string;
  readonly holderDocument: string;
  readonly slotsCount: number;
  readonly participants: readonly OfflineRosterParticipant[];
  readonly emergencyContact: OfflineEmergencyContact;
  /** Fecha operativa `YYYY-MM-DD` en `America/Bogota` para purga automática > 24 h. */
  readonly rosterDate?: string;
  /** Marca local anti-doble canje inmediato en padrón (`CONSUMIDO_LOCAL`, T-2.11 / RNF-18). */
  readonly localStatus?: 'ISSUED' | 'CONSUMIDO_LOCAL';
  readonly consumedLocallyAt?: string;
}

/** 3. Object Store: `revocation_list` (`keyPath: 'voucherId'`). */
export interface OfflineRevocationEntry {
  readonly voucherId: string;
  /** Claim `jti` del voucher revocado (ADR-0033 §1). Indexado de forma única en `idx_revocation_jti`. */
  readonly jti: string;
  readonly shortCode: string;
  readonly revokedAt: string;
  readonly revocationReason: string;
}

/** 4. Object Store: `public_signing_keys` (`keyPath: 'kid'`). */
export interface OfflineSigningKeyEntry {
  readonly kid: string;
  readonly algorithm: 'RS256' | 'Ed25519';
  readonly publicKeyPem: string;
  readonly status: 'ACTIVE' | 'RETIRED';
  readonly validFrom: string;
}

/** 5. Object Store: `local_validations` (`keyPath: 'localValidationId'`). */
export type OfflineValidationChannel = 'OFFLINE_PWA' | 'MANUAL_CODE';
export type OfflineLocalResult =
  'VALIDATED_OFFLINE' | 'REJECTED_REVOKED' | 'REJECTED_ALREADY_CONSUMED';
export type OfflineValidationSyncStatus = 'PENDING' | 'SYNCED' | 'CONFLICT' | 'REJECTED';

export interface OfflineValidationEntry {
  readonly localValidationId: string;
  /** Identificador del voucher (`jti` en el contrato de sincronización de ADR-0033 §1). */
  readonly voucherId: string;
  readonly shortCode: string;
  readonly deviceId: string;
  readonly validatedBy: string;
  readonly channel: OfflineValidationChannel;
  readonly localResult: OfflineLocalResult;
  /** Estado de consumo local atómico (RNF-18 / T-2.11). */
  readonly consumptionMarker: 'CONSUMIDO_LOCAL' | 'REJECTED';
  readonly signatureSha256: string | null;
  readonly scannedAt: string;
  readonly syncStatus: OfflineValidationSyncStatus;
  readonly serverOutcome?: string;
  readonly incidentId?: string | null;
  readonly syncMessage?: string | null;
  readonly syncedAt?: string;
}

/** 6. Object Store: `sync_outbox` (`keyPath: 'mutationId'`). */
export type OutboxAggregateType = 'OFFLINE_VALIDATION' | 'INCIDENT_REPORT';
export type OutboxActionType = 'CREATE_VALIDATION' | 'FLAG_SUSPICIOUS';
export type OutboxStatus = 'QUEUED' | 'PROCESSING' | 'FAILED_RETRYABLE' | 'DEAD_LETTER';

export interface SyncOutboxEntry {
  readonly mutationId: string;
  readonly sequenceNumber: number;
  readonly syncBatchId: string | null;
  readonly aggregateType: OutboxAggregateType;
  readonly aggregateId: string;
  readonly actionType: OutboxActionType;
  readonly payload: Readonly<Record<string, unknown>>;
  readonly status: OutboxStatus;
  readonly retryCount: number;
  readonly maxRetries: number;
  readonly nextRetryAt?: number;
  readonly lastError: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
}

/** Metadatos ligeros no sensibles persistidos en `integrate.offline.meta.v1` (< 1 KB, cero tokens/PII). */
export interface RosterDownloadMeta {
  readonly rosterDate: string;
  readonly generatedAt: string;
  readonly associationId: string;
  readonly revocationsWatermark: string | null;
  readonly voucherCount: number;
}

export interface OfflineDeviceMeta {
  readonly deviceId: string;
  readonly rosterMeta: RosterDownloadMeta | null;
  readonly catalogUpdatedAt: string | null;
  readonly clockSkewSeconds: number;
  readonly lastActivityAt: string;
  readonly nextSequenceNumber: number;
  readonly lastSyncedAt?: string | null;
}

/** Estado diagnóstico de cuota y persistencia del navegador (`StorageManager`). */
export interface OfflineStorageDiagnostics {
  readonly usageBytes: number;
  readonly quotaBytes: number;
  readonly maxBudgetBytes: number;
  readonly usageRatio: number;
  readonly nearLimit: boolean;
  readonly exceeded: boolean;
  /** `true` si el SO concedió `StorageManager.persist()`, `false` si opera en modo volátil. */
  readonly persisted: boolean | null;
  readonly degradedReadOnly: boolean;
}
