-- =============================================================================
-- V6 · Módulo voucher: llaves públicas de firma y vouchers criptográficos.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- voucher.signing_keys (rotación de llaves; G2)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS voucher.signing_keys (
    id              SMALLINT      GENERATED ALWAYS AS IDENTITY,
    kid             VARCHAR(64)   NOT NULL,
    algorithm       VARCHAR(10)   NOT NULL,
    public_key_pem  TEXT          NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    valid_from      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    retired_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by      UUID          DEFAULT common.current_actor(),
    CONSTRAINT pk_signing_keys PRIMARY KEY (id),
    CONSTRAINT uq_signing_keys_kid UNIQUE (kid),
    CONSTRAINT ck_signing_keys_kid CHECK (kid ~ '^[A-Za-z0-9._-]{4,64}$'),
    CONSTRAINT ck_signing_keys_algorithm CHECK (algorithm IN ('RS256', 'EdDSA')),
    CONSTRAINT ck_signing_keys_pem CHECK (public_key_pem LIKE '-----BEGIN PUBLIC KEY-----%'),
    CONSTRAINT ck_signing_keys_status CHECK (status IN ('ACTIVE', 'RETIRED', 'COMPROMISED')),
    CONSTRAINT ck_signing_keys_retired CHECK ((status = 'ACTIVE') = (retired_at IS NULL)),
    CONSTRAINT fk_signing_keys_created_by FOREIGN KEY (created_by)
        REFERENCES auth.users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_signing_keys_one_active
    ON voucher.signing_keys (status) WHERE status = 'ACTIVE';
SELECT common.attach_updated_at('voucher.signing_keys');

COMMENT ON TABLE  voucher.signing_keys IS 'Llaves PÚBLICAS de verificación de vouchers, identificadas por kid. La llave privada vive solo en el backend o en KMS (RNF-05) y nunca se guarda en la BD.';
COMMENT ON COLUMN voucher.signing_keys.status IS 'ACTIVE (firma nuevos vouchers; solo una) | RETIRED (solo verifica) | COMPROMISED (sus vouchers deben revocarse).';
COMMENT ON COLUMN voucher.signing_keys.public_key_pem IS 'Llave pública SPKI en PEM que la PWA guarda en crypto_keys_store (RF-16).';

-- -----------------------------------------------------------------------------
-- voucher.vouchers (1:1 con reserva confirmada; G1)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS voucher.vouchers (
    id                 UUID               NOT NULL DEFAULT gen_random_uuid(),
    booking_id         UUID               NOT NULL,
    jti                UUID               NOT NULL DEFAULT gen_random_uuid(),
    signing_key_id     SMALLINT           NOT NULL,
    token_sha256       common.sha256_hex  NOT NULL,
    short_code         VARCHAR(8)         NOT NULL,
    status             VARCHAR(20)        NOT NULL DEFAULT 'ISSUED',
    expires_at         TIMESTAMPTZ        NOT NULL,
    pdf_s3_key         common.s3_key,
    revoked_at         TIMESTAMPTZ,
    revocation_reason  VARCHAR(255),
    consumed_at        TIMESTAMPTZ,
    created_at         TIMESTAMPTZ        NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ        NOT NULL DEFAULT now(),
    created_by         UUID               DEFAULT common.current_actor(),
    CONSTRAINT pk_vouchers PRIMARY KEY (id),
    CONSTRAINT uq_vouchers_booking UNIQUE (booking_id),
    CONSTRAINT uq_vouchers_jti UNIQUE (jti),
    CONSTRAINT uq_vouchers_token UNIQUE (token_sha256),
    CONSTRAINT uq_vouchers_short_code UNIQUE (short_code),
    CONSTRAINT ck_vouchers_short_code CHECK (short_code ~ '^[A-HJ-NP-Z2-9]{8}$'),
    CONSTRAINT ck_vouchers_status CHECK (status IN ('ISSUED', 'REVOKED', 'CONSUMED', 'EXPIRED')),
    CONSTRAINT ck_vouchers_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_vouchers_revocation_pair CHECK ((revoked_at IS NULL) = (revocation_reason IS NULL)),
    CONSTRAINT ck_vouchers_revoked CHECK ((status = 'REVOKED') = (revoked_at IS NOT NULL)),
    CONSTRAINT ck_vouchers_consumed CHECK (status <> 'CONSUMED' OR consumed_at IS NOT NULL),
    CONSTRAINT fk_vouchers_booking FOREIGN KEY (booking_id)
        REFERENCES booking.bookings (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_vouchers_signing_key FOREIGN KEY (signing_key_id)
        REFERENCES voucher.signing_keys (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_vouchers_created_by FOREIGN KEY (created_by)
        REFERENCES auth.users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

-- Lista delta de revocación (RF-16): "revocados después de <watermark>".
CREATE INDEX IF NOT EXISTS ix_vouchers_revoked_at
    ON voucher.vouchers (revoked_at) WHERE revoked_at IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_vouchers_signing_key ON voucher.vouchers (signing_key_id);
CREATE INDEX IF NOT EXISTS ix_vouchers_issued_open
    ON voucher.vouchers (expires_at) WHERE status = 'ISSUED';
SELECT common.attach_updated_at('voucher.vouchers');

COMMENT ON TABLE  voucher.vouchers IS 'Voucher intransferible de una reserva CONFIRMED: JWT firmado (RS256/EdDSA) en QR y PDF (RF-15). Uno por reserva, válido para slots_count personas.';
COMMENT ON COLUMN voucher.vouchers.jti IS 'Claim jti del JWT; es la llave de las listas de revocación y consumo de la PWA.';
COMMENT ON COLUMN voucher.vouchers.token_sha256 IS 'Hash del JWT compacto emitido. El JWT completo no se almacena (G2).';
COMMENT ON COLUMN voucher.vouchers.short_code IS 'Código alfanumérico de 8 caracteres sin ambigüedades (sin I, O, 0, 1) para el ingreso manual de contingencia (R-02).';
COMMENT ON COLUMN voucher.vouchers.pdf_s3_key IS 'Clave S3 del PDF oficial; la URL firmada se genera en cada descarga (G3). NULL mientras se genera de forma asíncrona.';
COMMENT ON COLUMN voucher.vouchers.created_at IS 'Fecha de emisión (claim iat).';
COMMENT ON COLUMN voucher.vouchers.revoked_at IS 'Marca usada para la descarga delta de la lista de revocación (G4).';

CREATE OR REPLACE FUNCTION voucher.fn_voucher_guard()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_booking_status  VARCHAR(30);
    v_key_status      VARCHAR(20);
BEGIN
    IF TG_OP = 'INSERT' THEN
        SELECT b.status INTO v_booking_status FROM booking.bookings b WHERE b.id = NEW.booking_id;
        IF v_booking_status IS DISTINCT FROM 'CONFIRMED' THEN
            RAISE EXCEPTION 'Solo se emite voucher para reservas CONFIRMED (estado %)', v_booking_status
                USING ERRCODE = 'check_violation';
        END IF;
        SELECT k.status INTO v_key_status FROM voucher.signing_keys k WHERE k.id = NEW.signing_key_id;
        IF v_key_status IS DISTINCT FROM 'ACTIVE' THEN
            RAISE EXCEPTION 'La llave % no está activa para firmar', NEW.signing_key_id
                USING ERRCODE = 'check_violation';
        END IF;
        IF NEW.status <> 'ISSUED' THEN
            RAISE EXCEPTION 'Un voucher nace en estado ISSUED' USING ERRCODE = 'check_violation';
        END IF;
        RETURN NEW;
    END IF;

    IF ROW(NEW.booking_id, NEW.jti, NEW.signing_key_id, NEW.token_sha256, NEW.short_code, NEW.expires_at)
       IS DISTINCT FROM
       ROW(OLD.booking_id, OLD.jti, OLD.signing_key_id, OLD.token_sha256, OLD.short_code, OLD.expires_at) THEN
        RAISE EXCEPTION 'Los datos firmados del voucher % son inmutables', OLD.id USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.status IS DISTINCT FROM OLD.status
       AND (OLD.status, NEW.status) NOT IN (
            ('ISSUED', 'REVOKED'), ('ISSUED', 'CONSUMED'), ('ISSUED', 'EXPIRED')) THEN
        RAISE EXCEPTION 'Transición de voucher no permitida: % -> %', OLD.status, NEW.status
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE TRIGGER trg_voucher_guard
    BEFORE INSERT OR UPDATE ON voucher.vouchers
    FOR EACH ROW EXECUTE FUNCTION voucher.fn_voucher_guard();
