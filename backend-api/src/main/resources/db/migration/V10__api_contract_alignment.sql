-- =============================================================================
-- V10 · Alineación del modelo con el Contrato de API v1.0.0
--       (docs/api/openapi.yaml y docs/api/dtos_contrato_api.md §8, aprobado
--       el 2026-10-05).
--
-- Las tablas append-only no admiten UPDATE: si ya contienen valores de catálogo
-- antiguos, la migración se detiene con un mensaje explícito en lugar de
-- reescribir evidencia legal.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. auth.roles: el código del anfitrión pasa a HOST (claim ROLE_HOST, A3).
-- -----------------------------------------------------------------------------
UPDATE auth.roles SET code = 'HOST' WHERE code = 'COMMUNITY_HOST';

-- -----------------------------------------------------------------------------
-- 2. auth.users: estado de credencial para el registro asistido (C2).
-- -----------------------------------------------------------------------------
ALTER TABLE auth.users
    ADD COLUMN IF NOT EXISTS credential_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE auth.users
    ADD CONSTRAINT ck_users_credential_status CHECK (credential_status IN ('ACTIVE', 'PENDING_RESET'));
GRANT SELECT (credential_status) ON auth.users TO integrate_readonly;

COMMENT ON COLUMN auth.users.credential_status IS 'ACTIVE | PENDING_RESET (anfitrión registrado por un gestor con contraseña aleatoria; debe restablecerla antes del primer login).';

-- -----------------------------------------------------------------------------
-- 3. auth.habeas_data_consents: finalidades del contrato y consentimiento
--    capturado por intermediación del gestor (B5, C4, E5).
-- -----------------------------------------------------------------------------
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM auth.habeas_data_consents
                WHERE purpose IN ('SENSITIVE_VICTIM_DATA', 'HEALTH_DATA')) THEN
        RAISE EXCEPTION 'V10: hay consentimientos con finalidades antiguas (SENSITIVE_VICTIM_DATA/HEALTH_DATA). La tabla es append-only; depure el entorno antes de migrar.';
    END IF;
END;
$$;

ALTER TABLE auth.habeas_data_consents DROP CONSTRAINT ck_consents_purpose;
ALTER TABLE auth.habeas_data_consents
    ADD CONSTRAINT ck_consents_purpose CHECK (purpose IN ('PERSONAL_DATA', 'SENSITIVE_VICTIM', 'HEALTH_EMERGENCY'));

ALTER TABLE auth.habeas_data_consents
    ADD COLUMN IF NOT EXISTS channel                      VARCHAR(20)   NOT NULL DEFAULT 'SELF_SERVICE',
    ADD COLUMN IF NOT EXISTS captured_by                  UUID,
    ADD COLUMN IF NOT EXISTS physical_consent_form_s3_key common.s3_key;
ALTER TABLE auth.habeas_data_consents
    ADD CONSTRAINT ck_consents_channel CHECK (channel IN ('SELF_SERVICE', 'ASSISTED_PROXY')),
    ADD CONSTRAINT ck_consents_assisted CHECK (
        (channel = 'ASSISTED_PROXY') = (captured_by IS NOT NULL AND physical_consent_form_s3_key IS NOT NULL)),
    ADD CONSTRAINT fk_consents_captured_by FOREIGN KEY (captured_by)
        REFERENCES auth.users (id) ON DELETE RESTRICT ON UPDATE RESTRICT;

COMMENT ON COLUMN auth.habeas_data_consents.purpose IS 'PERSONAL_DATA (todos) | SENSITIVE_VICTIM (anfitriones) | HEALTH_EMERGENCY (turistas: EPS y contacto de emergencia; precondición de /bookings/lock).';
COMMENT ON COLUMN auth.habeas_data_consents.channel IS 'SELF_SERVICE (el titular en su propia sesión) | ASSISTED_PROXY (capturado por un gestor de enlace en registro asistido).';
COMMENT ON COLUMN auth.habeas_data_consents.captured_by IS 'Gestor o administrador que capturó el consentimiento asistido. ip_address y user_agent son los de su terminal.';
COMMENT ON COLUMN auth.habeas_data_consents.physical_consent_form_s3_key IS 'Formato físico firmado por el titular (PDF en S3); obligatorio en ASSISTED_PROXY.';

-- -----------------------------------------------------------------------------
-- 4. booking.bookings: idempotencia del lock (E3) y anulación de pago sobre una
--    reserva PENDING (F5: VOIDED -> CANCELLED_REFUNDED, libera cupos).
-- -----------------------------------------------------------------------------
ALTER TABLE booking.bookings
    ADD COLUMN IF NOT EXISTS idempotency_key UUID,
    ADD COLUMN IF NOT EXISTS request_sha256  common.sha256_hex;
ALTER TABLE booking.bookings
    ADD CONSTRAINT ck_bookings_idempotency CHECK ((idempotency_key IS NULL) = (request_sha256 IS NULL));
CREATE UNIQUE INDEX IF NOT EXISTS uq_bookings_idempotency
    ON booking.bookings (tourist_id, idempotency_key) WHERE idempotency_key IS NOT NULL;

COMMENT ON COLUMN booking.bookings.idempotency_key IS 'Cabecera Idempotency-Key de POST /bookings/lock. Dentro de 24 h un reintento con el mismo request_sha256 reproduce la reserva; cualquier otro uso de la clave responde 409.';
COMMENT ON COLUMN booking.bookings.request_sha256 IS 'Hash del payload canónico del lock, para distinguir un reintento de una reutilización de la clave.';

CREATE OR REPLACE FUNCTION booking.fn_booking_update_guard()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF ROW(NEW.tourist_id, NEW.schedule_id, NEW.slots_count, NEW.unit_price_cop,
           NEW.total_amount_cop, NEW.currency, NEW.expires_at, NEW.idempotency_key, NEW.request_sha256)
       IS DISTINCT FROM
       ROW(OLD.tourist_id, OLD.schedule_id, OLD.slots_count, OLD.unit_price_cop,
           OLD.total_amount_cop, OLD.currency, OLD.expires_at, OLD.idempotency_key, OLD.request_sha256) THEN
        RAISE EXCEPTION 'Los datos económicos de la reserva % son inmutables', OLD.id
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.status IS DISTINCT FROM OLD.status THEN
        IF (OLD.status, NEW.status) NOT IN (
            ('PENDING', 'CONFIRMED'), ('PENDING', 'PAYMENT_FAILED'), ('PENDING', 'EXPIRED'),
            ('PENDING', 'CANCELLED_BY_PLATFORM'), ('PENDING', 'CANCELLED_REFUNDED'),
            ('CONFIRMED', 'CANCELLED_REFUNDED'), ('CONFIRMED', 'CANCELLED_NO_REFUND'),
            ('CONFIRMED', 'CANCELLED_BY_PLATFORM'), ('CONFIRMED', 'EXECUTED'), ('CONFIRMED', 'NO_SHOW'),
            ('NO_SHOW', 'EXECUTED'),
            ('CANCELLED_REFUNDED', 'SYNC_CONFLICT'), ('CANCELLED_NO_REFUND', 'SYNC_CONFLICT'),
            ('CANCELLED_BY_PLATFORM', 'SYNC_CONFLICT')) THEN
            RAISE EXCEPTION 'Transición de reserva no permitida: % -> %', OLD.status, NEW.status
                USING ERRCODE = 'check_violation';
        END IF;
        IF NEW.status = 'CONFIRMED' AND NEW.confirmed_at IS NULL THEN
            NEW.confirmed_at := now();
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

-- -----------------------------------------------------------------------------
-- 5. payments.payment_webhook_events: la clave de idempotencia es el eventId.
-- -----------------------------------------------------------------------------
COMMENT ON COLUMN payments.payment_webhook_events.idempotency_key IS 'eventId del webhook (canónico) o derivado de signature.checksum (Wompi). Único por proveedor (F3).';

-- -----------------------------------------------------------------------------
-- 6. offline.devices: también puede pertenecer a un anfitrión (G8) e
--    identificador de hasta 64 caracteres (G3).
-- -----------------------------------------------------------------------------
ALTER TABLE offline.devices ALTER COLUMN manager_id DROP NOT NULL;
ALTER TABLE offline.devices ADD COLUMN IF NOT EXISTS host_id UUID;
ALTER TABLE offline.devices
    ADD CONSTRAINT ck_devices_owner CHECK (num_nonnulls(manager_id, host_id) = 1),
    ADD CONSTRAINT fk_devices_host FOREIGN KEY (host_id)
        REFERENCES catalog.host_profiles (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
CREATE INDEX IF NOT EXISTS ix_devices_host ON offline.devices (host_id) WHERE host_id IS NOT NULL;

ALTER TABLE offline.devices DROP CONSTRAINT ck_devices_identifier;
ALTER TABLE offline.devices ALTER COLUMN device_identifier TYPE VARCHAR(64);
ALTER TABLE offline.devices
    ADD CONSTRAINT ck_devices_identifier CHECK (device_identifier ~ '^[A-Za-z0-9._-]{4,64}$');

COMMENT ON TABLE  offline.devices IS 'Dispositivo móvil registrado de un gestor de enlace o de un anfitrión (exactamente uno de los dos). Si se pierde se revoca (LOST) y sus lotes posteriores se rechazan.';
COMMENT ON COLUMN offline.devices.host_id IS 'Anfitrión dueño del dispositivo cuando valida vouchers sin gestor (G8).';

-- -----------------------------------------------------------------------------
-- 7. offline.sync_batches: evidencia para la idempotencia por contenido (G7).
-- -----------------------------------------------------------------------------
ALTER TABLE offline.sync_batches
    ADD COLUMN IF NOT EXISTS device_sent_at  TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS payload_sha256  common.sha256_hex,
    ADD COLUMN IF NOT EXISTS result_payload  JSONB;

COMMENT ON COLUMN offline.sync_batches.device_sent_at IS 'deviceSentAt reportado por la PWA; created_at − device_sent_at = desfase de reloj (G4).';
COMMENT ON COLUMN offline.sync_batches.payload_sha256 IS 'Hash del payload canónico. Reenvío con el mismo hash → 200 con result_payload; con otro hash → 409 SYNC_BATCH_PAYLOAD_MISMATCH.';
COMMENT ON COLUMN offline.sync_batches.result_payload IS 'Respuesta OfflineSyncBatchResponse emitida, para reproducirla de forma idéntica.';

-- -----------------------------------------------------------------------------
-- 8. offline.offline_validations: resultados del servidor del contrato (G4–G6),
--    código manual dentro de un lote offline y llave local de la PWA.
-- -----------------------------------------------------------------------------
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM offline.offline_validations
                WHERE server_outcome IN ('SYNCHRONIZED', 'CONFLICT', 'DUPLICATE', 'REJECTED')) THEN
        RAISE EXCEPTION 'V10: hay validaciones con server_outcome antiguo. La tabla es append-only; depure el entorno antes de migrar.';
    END IF;
    IF EXISTS (SELECT 1 FROM offline.sync_incidents
                WHERE incident_type IN ('CANCELLED_BUT_CONSUMED', 'DOUBLE_REDEMPTION',
                                        'REVOKED_VOUCHER_USED', 'CONSUMED_AFTER_EXPIRY')) THEN
        RAISE EXCEPTION 'V10: hay incidentes con incident_type antiguo; reclasifíquelos antes de migrar.';
    END IF;
END;
$$;

ALTER TABLE offline.offline_validations DROP CONSTRAINT ck_offline_validations_outcome;
ALTER TABLE offline.offline_validations ALTER COLUMN server_outcome TYPE VARCHAR(30);
ALTER TABLE offline.offline_validations
    ADD CONSTRAINT ck_offline_validations_outcome CHECK (server_outcome IN (
        'ACCEPTED', 'REJECTED_REVOKED', 'REJECTED_DUPLICATE',
        'REJECTED_OUT_OF_DATE', 'REJECTED_CLOCK_SKEW'));

ALTER TABLE offline.offline_validations DROP CONSTRAINT ck_offline_validations_offline_fields;
ALTER TABLE offline.offline_validations
    ADD CONSTRAINT ck_offline_validations_offline_fields CHECK (
        (channel = 'OFFLINE_PWA' AND sync_batch_id IS NOT NULL AND device_id IS NOT NULL AND signature_sha256 IS NOT NULL)
        OR (channel = 'MANUAL_CODE' AND (sync_batch_id IS NULL OR device_id IS NOT NULL))
        OR (channel = 'ONLINE' AND sync_batch_id IS NULL));

ALTER TABLE offline.offline_validations ADD COLUMN IF NOT EXISTS local_validation_id UUID;
ALTER TABLE offline.offline_validations
    ADD CONSTRAINT ck_offline_validations_local_id CHECK (sync_batch_id IS NULL OR local_validation_id IS NOT NULL);
CREATE UNIQUE INDEX IF NOT EXISTS uq_offline_validations_local_id
    ON offline.offline_validations (local_validation_id) WHERE local_validation_id IS NOT NULL;

COMMENT ON COLUMN offline.offline_validations.server_outcome IS 'ACCEPTED | REJECTED_REVOKED (+ incidente OVER_REDEMPTION_COMMUNITY_PROTECTED) | REJECTED_DUPLICATE (+ DUPLICATE_OFFLINE_REDEMPTION) | REJECTED_OUT_OF_DATE | REJECTED_CLOCK_SKEW (+ CLOCK_SKEW_FUTURE_SCAN). Un voucher inexistente no se persiste aquí (FK) sino en audit.audit_trail.';
COMMENT ON COLUMN offline.offline_validations.channel IS 'OFFLINE_PWA (QR escaneado y encolado) | MANUAL_CODE (short_code digitado; puede llegar en un lote offline) | ONLINE (validación con conexión).';
COMMENT ON COLUMN offline.offline_validations.local_validation_id IS 'localValidationId generado por la PWA; obligatorio en validaciones que llegan por lote.';
COMMENT ON COLUMN offline.offline_validations.local_result IS 'Veredicto que mostró la PWA. Por lote solo llegan canjes aceptados en campo (RF-17), así que el servidor registra ACCEPTED.';

-- -----------------------------------------------------------------------------
-- 9. offline.sync_incidents: tipos de incidente del contrato.
-- -----------------------------------------------------------------------------
ALTER TABLE offline.sync_incidents DROP CONSTRAINT ck_sync_incidents_type;
ALTER TABLE offline.sync_incidents ALTER COLUMN incident_type TYPE VARCHAR(40);
ALTER TABLE offline.sync_incidents
    ADD CONSTRAINT ck_sync_incidents_type CHECK (incident_type IN (
        'OVER_REDEMPTION_COMMUNITY_PROTECTED', 'DUPLICATE_OFFLINE_REDEMPTION', 'CLOCK_SKEW_FUTURE_SCAN'));

COMMENT ON COLUMN offline.sync_incidents.incident_type IS 'OVER_REDEMPTION_COMMUNITY_PROTECTED (voucher revocado usado en campo; HIGH; 85% liberado a la comunidad) | DUPLICATE_OFFLINE_REDEMPTION (doble canje; gana el scannedAt más temprano) | CLOCK_SKEW_FUTURE_SCAN (scannedAt > now() + 5 min).';
