-- =============================================================================
-- V7 · Módulo offline: dispositivos de gestores, descargas de padrón, lotes de
--      sincronización, validaciones de voucher e incidentes de sincronización.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- offline.devices (C4)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS offline.devices (
    id                 UUID           NOT NULL DEFAULT gen_random_uuid(),
    device_identifier  VARCHAR(100)   NOT NULL,
    manager_id         UUID           NOT NULL,
    label              VARCHAR(100),
    platform           VARCHAR(10)    NOT NULL,
    status             VARCHAR(10)    NOT NULL DEFAULT 'ACTIVE',
    revoked_at         TIMESTAMPTZ,
    last_seen_at       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by         UUID           DEFAULT common.current_actor(),
    CONSTRAINT pk_devices PRIMARY KEY (id),
    CONSTRAINT uq_devices_identifier UNIQUE (device_identifier),
    CONSTRAINT ck_devices_identifier CHECK (device_identifier ~ '^[A-Za-z0-9._-]{4,100}$'),
    CONSTRAINT ck_devices_platform CHECK (platform IN ('ANDROID', 'IOS', 'OTHER')),
    CONSTRAINT ck_devices_status CHECK (status IN ('ACTIVE', 'REVOKED', 'LOST')),
    CONSTRAINT ck_devices_revoked CHECK ((status = 'ACTIVE') = (revoked_at IS NULL)),
    CONSTRAINT fk_devices_manager FOREIGN KEY (manager_id)
        REFERENCES catalog.link_manager_profiles (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_devices_created_by FOREIGN KEY (created_by)
        REFERENCES auth.users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX IF NOT EXISTS ix_devices_manager ON offline.devices (manager_id);
SELECT common.attach_updated_at('offline.devices');

COMMENT ON TABLE  offline.devices IS 'Dispositivo móvil registrado de un gestor de enlace. Pertenece a un único gestor de forma permanente; si se pierde se revoca (LOST) y sus lotes posteriores se rechazan.';
COMMENT ON COLUMN offline.devices.device_identifier IS 'Identificador enviado por la PWA en deviceId (p. ej. GESTOR-MOVIL-004).';

-- -----------------------------------------------------------------------------
-- offline.roster_downloads (H2; append-only)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS offline.roster_downloads (
    id                     BIGINT         GENERATED ALWAYS AS IDENTITY,
    device_id              UUID           NOT NULL,
    association_id         UUID           NOT NULL,
    roster_date            DATE           NOT NULL,
    signing_key_id         SMALLINT       NOT NULL,
    vouchers_included      INTEGER        NOT NULL,
    revocations_watermark  TIMESTAMPTZ,
    downloaded_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT pk_roster_downloads PRIMARY KEY (id),
    CONSTRAINT ck_roster_downloads_count CHECK (vouchers_included >= 0),
    CONSTRAINT fk_roster_downloads_device FOREIGN KEY (device_id)
        REFERENCES offline.devices (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_roster_downloads_association FOREIGN KEY (association_id)
        REFERENCES catalog.community_associations (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_roster_downloads_signing_key FOREIGN KEY (signing_key_id)
        REFERENCES voucher.signing_keys (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX IF NOT EXISTS ix_roster_downloads_device ON offline.roster_downloads (device_id, downloaded_at DESC);
CREATE INDEX IF NOT EXISTS ix_roster_downloads_association ON offline.roster_downloads (association_id, roster_date);
CREATE INDEX IF NOT EXISTS ix_roster_downloads_signing_key ON offline.roster_downloads (signing_key_id);
SELECT common.make_append_only('offline.roster_downloads');

COMMENT ON TABLE  offline.roster_downloads IS 'Traza de cada descarga preventiva del padrón diario (RF-16): qué dispositivo recibió qué vouchers y hasta qué punto de la lista de revocación.';
COMMENT ON COLUMN offline.roster_downloads.revocations_watermark IS 'Máximo voucher.vouchers.revoked_at incluido; la siguiente descarga delta parte de aquí.';

-- -----------------------------------------------------------------------------
-- offline.sync_batches (H1; idempotencia por PK generada en el cliente)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS offline.sync_batches (
    id              UUID           NOT NULL,
    device_id       UUID           NOT NULL,
    items_received  INTEGER        NOT NULL,
    status          VARCHAR(20)    NOT NULL DEFAULT 'RECEIVED',
    processed_at    TIMESTAMPTZ,
    error_detail    TEXT,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT pk_sync_batches PRIMARY KEY (id),
    CONSTRAINT ck_sync_batches_items CHECK (items_received >= 0),
    CONSTRAINT ck_sync_batches_status CHECK (status IN ('RECEIVED', 'PROCESSED', 'PARTIAL', 'FAILED')),
    CONSTRAINT ck_sync_batches_processed CHECK (status = 'RECEIVED' OR processed_at IS NOT NULL),
    CONSTRAINT fk_sync_batches_device FOREIGN KEY (device_id)
        REFERENCES offline.devices (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX IF NOT EXISTS ix_sync_batches_device ON offline.sync_batches (device_id, created_at DESC);
SELECT common.attach_updated_at('offline.sync_batches');

COMMENT ON TABLE  offline.sync_batches IS 'Lote de asistencias enviado por Background Sync a /api/v1/offline/sync. La PK es el syncBatchId del cliente: un reenvío choca con la PK y se responde con el resultado ya registrado (RNF-06).';
COMMENT ON COLUMN offline.sync_batches.created_at IS 'Momento de recepción del lote en el servidor.';

-- -----------------------------------------------------------------------------
-- offline.offline_validations (todo escaneo, incluidos dobles canjes; H1, H4)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS offline.offline_validations (
    id                BIGINT              GENERATED ALWAYS AS IDENTITY,
    voucher_id        UUID                NOT NULL,
    channel           VARCHAR(15)         NOT NULL,
    sync_batch_id     UUID,
    device_id         UUID,
    validated_by      UUID                NOT NULL,
    scanned_at        TIMESTAMPTZ         NOT NULL,
    signature_sha256  common.sha256_hex,
    local_result      VARCHAR(25)         NOT NULL,
    server_outcome    VARCHAR(20)         NOT NULL,
    created_at        TIMESTAMPTZ         NOT NULL DEFAULT now(),
    CONSTRAINT pk_offline_validations PRIMARY KEY (id),
    CONSTRAINT uq_offline_validations_scan UNIQUE (voucher_id, device_id, scanned_at),
    CONSTRAINT ck_offline_validations_channel CHECK (channel IN ('OFFLINE_PWA', 'ONLINE', 'MANUAL_CODE')),
    CONSTRAINT ck_offline_validations_offline_fields CHECK (
        (channel = 'OFFLINE_PWA' AND sync_batch_id IS NOT NULL AND device_id IS NOT NULL AND signature_sha256 IS NOT NULL)
        OR (channel <> 'OFFLINE_PWA' AND sync_batch_id IS NULL)
    ),
    CONSTRAINT ck_offline_validations_local_result CHECK (local_result IN
        ('ACCEPTED', 'REJECTED_DUPLICATE', 'REJECTED_REVOKED', 'REJECTED_WRONG_DATE', 'REJECTED_SIGNATURE')),
    CONSTRAINT ck_offline_validations_outcome CHECK (server_outcome IN ('SYNCHRONIZED', 'CONFLICT', 'DUPLICATE', 'REJECTED')),
    CONSTRAINT fk_offline_validations_voucher FOREIGN KEY (voucher_id)
        REFERENCES voucher.vouchers (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_offline_validations_batch FOREIGN KEY (sync_batch_id)
        REFERENCES offline.sync_batches (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_offline_validations_device FOREIGN KEY (device_id)
        REFERENCES offline.devices (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_offline_validations_validated_by FOREIGN KEY (validated_by)
        REFERENCES auth.users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX IF NOT EXISTS ix_offline_validations_voucher ON offline.offline_validations (voucher_id, scanned_at);
CREATE INDEX IF NOT EXISTS ix_offline_validations_batch ON offline.offline_validations (sync_batch_id);
CREATE INDEX IF NOT EXISTS ix_offline_validations_device ON offline.offline_validations (device_id);
CREATE INDEX IF NOT EXISTS ix_offline_validations_validated_by ON offline.offline_validations (validated_by);
SELECT common.make_append_only('offline.offline_validations');

COMMENT ON TABLE  offline.offline_validations IS 'Cada escaneo o validación de voucher en campo (offline), en línea o por código manual. voucher_id NO es único: los dobles canjes se registran como evidencia de fraude (H1, RF-17).';
COMMENT ON COLUMN offline.offline_validations.channel IS 'OFFLINE_PWA (cola diferida de la PWA) | ONLINE (validación con conexión) | MANUAL_CODE (contingencia por short_code).';
COMMENT ON COLUMN offline.offline_validations.scanned_at IS 'Hora del canje según el dispositivo en territorio (puede ser muy anterior a created_at).';
COMMENT ON COLUMN offline.offline_validations.signature_sha256 IS 'signatureHash reportado por la PWA: hash de la firma del JWT escaneado.';
COMMENT ON COLUMN offline.offline_validations.local_result IS 'Veredicto que mostró la PWA en campo (CONSUMIDO_LOCAL = ACCEPTED).';
COMMENT ON COLUMN offline.offline_validations.server_outcome IS 'Resultado de la conciliación en el servidor; CONFLICT genera un registro en offline.sync_incidents.';

-- -----------------------------------------------------------------------------
-- offline.sync_incidents (H3, RF-18, CU-19)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS offline.sync_incidents (
    id                    UUID               NOT NULL DEFAULT gen_random_uuid(),
    validation_id         BIGINT             NOT NULL,
    incident_type         VARCHAR(30)        NOT NULL,
    severity              VARCHAR(10)        NOT NULL,
    status                VARCHAR(15)        NOT NULL DEFAULT 'OPEN',
    financial_impact_cop  common.cop_amount,
    resolution_notes      TEXT,
    resolved_by           UUID,
    resolved_at           TIMESTAMPTZ,
    created_at            TIMESTAMPTZ        NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ        NOT NULL DEFAULT now(),
    created_by            UUID               DEFAULT common.current_actor(),
    CONSTRAINT pk_sync_incidents PRIMARY KEY (id),
    CONSTRAINT uq_sync_incidents_validation UNIQUE (validation_id, incident_type),
    CONSTRAINT ck_sync_incidents_type CHECK (incident_type IN
        ('CANCELLED_BUT_CONSUMED', 'DOUBLE_REDEMPTION', 'REVOKED_VOUCHER_USED', 'CONSUMED_AFTER_EXPIRY')),
    CONSTRAINT ck_sync_incidents_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_sync_incidents_status CHECK (status IN ('OPEN', 'IN_REVIEW', 'RESOLVED', 'DISMISSED')),
    CONSTRAINT ck_sync_incidents_resolution CHECK (
        (status IN ('OPEN', 'IN_REVIEW') AND resolved_at IS NULL AND resolved_by IS NULL)
        OR (status IN ('RESOLVED', 'DISMISSED') AND resolved_at IS NOT NULL AND resolved_by IS NOT NULL
            AND resolution_notes IS NOT NULL)
    ),
    CONSTRAINT fk_sync_incidents_validation FOREIGN KEY (validation_id)
        REFERENCES offline.offline_validations (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_sync_incidents_resolved_by FOREIGN KEY (resolved_by)
        REFERENCES auth.users (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_sync_incidents_created_by FOREIGN KEY (created_by)
        REFERENCES auth.users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX IF NOT EXISTS ix_sync_incidents_open
    ON offline.sync_incidents (severity, created_at) WHERE status IN ('OPEN', 'IN_REVIEW');
CREATE INDEX IF NOT EXISTS ix_sync_incidents_resolved_by ON offline.sync_incidents (resolved_by);
SELECT common.attach_updated_at('offline.sync_incidents');

COMMENT ON TABLE  offline.sync_incidents IS 'Bitácora de contingencias de sincronización para el administrador (CU-19). La resolución nunca debita el 85% comunitario (RF-18).';
COMMENT ON COLUMN offline.sync_incidents.incident_type IS 'CANCELLED_BUT_CONSUMED (cancelada > 72 h pero usada en campo; severidad HIGH) | DOUBLE_REDEMPTION | REVOKED_VOUCHER_USED | CONSUMED_AFTER_EXPIRY.';
COMMENT ON COLUMN offline.sync_incidents.financial_impact_cop IS 'Pérdida que absorbe la plataforma (p. ej. reembolso ya pagado + 85% garantizado a la comunidad).';
