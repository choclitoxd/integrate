-- ADR-0054 / ADR-0060. Una política publicada puede tener un aviso inmutable.
-- La publicación requiere revisión del responsable. Esta migración no publica avisos.
CREATE TABLE auth.health_consent_notices (
    notice_version     VARCHAR(40)       PRIMARY KEY,
    policy_id          SMALLINT          NOT NULL REFERENCES auth.privacy_policies (id) ON DELETE RESTRICT,
    body               TEXT              NOT NULL CHECK (length(btrim(body)) > 0),
    content_sha256     common.sha256_hex NOT NULL,
    approved_for_host  BOOLEAN           NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_health_notice_policy UNIQUE (policy_id),
    CONSTRAINT uq_health_notice_policy_version UNIQUE (policy_id, notice_version),
    CONSTRAINT ck_health_notice_sha256 CHECK (content_sha256 = encode(sha256(convert_to(body, 'UTF8')), 'hex'))
);

SELECT common.make_append_only('auth.health_consent_notices');
REVOKE INSERT, UPDATE, DELETE ON auth.health_consent_notices FROM integrate_app;

ALTER TABLE auth.habeas_data_consents
    ADD COLUMN health_notice_version VARCHAR(40),
    ADD CONSTRAINT fk_consents_health_notice FOREIGN KEY (policy_id, health_notice_version)
        REFERENCES auth.health_consent_notices (policy_id, notice_version) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_consents_health_notice CHECK (
        health_notice_version IS NULL OR
        (purpose = 'HEALTH_EMERGENCY' AND action = 'GRANTED' AND channel = 'SELF_SERVICE')
    );

-- Serializa la captura/divulgación de EPS con la inserción de actos de salud, incluso
-- cuando una revocación se registra directamente mediante una operación administrativa.
CREATE FUNCTION auth.lock_health_consent(p_user_id UUID)
RETURNS VOID
LANGUAGE SQL
AS $$
    SELECT pg_advisory_xact_lock(54, hashtext(p_user_id::text))
$$;

CREATE FUNCTION auth.fn_health_consent_insert_lock()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    PERFORM auth.lock_health_consent(NEW.user_id);
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_health_consent_insert_lock
    BEFORE INSERT ON auth.habeas_data_consents
    FOR EACH ROW WHEN (NEW.purpose = 'HEALTH_EMERGENCY')
    EXECUTE FUNCTION auth.fn_health_consent_insert_lock();

ALTER TABLE booking.bookings
    ADD COLUMN health_consent_id BIGINT,
    ADD CONSTRAINT fk_bookings_health_consent FOREIGN KEY (health_consent_id)
        REFERENCES auth.habeas_data_consents (id) ON DELETE RESTRICT;

CREATE FUNCTION booking.fn_booking_health_consent_guard()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        IF NEW.health_consent_id IS DISTINCT FROM OLD.health_consent_id THEN
            RAISE EXCEPTION 'El consentimiento vinculado a la reserva es inmutable'
                USING ERRCODE = 'restrict_violation';
        END IF;
    ELSIF NEW.health_consent_id IS NOT NULL AND NOT EXISTS (
        SELECT 1
          FROM auth.habeas_data_consents c
          JOIN auth.health_consent_notices n ON n.notice_version = c.health_notice_version
         WHERE c.id = NEW.health_consent_id AND c.user_id = NEW.tourist_id
           AND c.purpose = 'HEALTH_EMERGENCY' AND c.action = 'GRANTED' AND c.channel = 'SELF_SERVICE'
           AND n.approved_for_host
    ) THEN
        RAISE EXCEPTION 'La reserva requiere un acto de salud versionado de su titular'
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_booking_health_consent_guard
    BEFORE INSERT OR UPDATE OF health_consent_id ON booking.bookings
    FOR EACH ROW EXECUTE FUNCTION booking.fn_booking_health_consent_guard();
