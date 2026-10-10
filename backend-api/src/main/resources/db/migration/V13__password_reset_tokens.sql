-- =============================================================================
-- V13 · Recuperación de contraseña (ADR-0014 §2, T-1.13a).
--
-- Tokens de un solo uso que llegan por correo en el fragmento de un enlace
-- (…/restablecer-contrasena#token=…). Solo se guarda su SHA-256.
-- =============================================================================

CREATE TABLE IF NOT EXISTS auth.password_reset_tokens (
    id            BIGINT             GENERATED ALWAYS AS IDENTITY,
    user_id       UUID               NOT NULL,
    token_sha256  common.sha256_hex  NOT NULL,
    requested_at  TIMESTAMPTZ        NOT NULL,
    expires_at    TIMESTAMPTZ        NOT NULL,
    consumed_at   TIMESTAMPTZ,
    revoked_at    TIMESTAMPTZ,
    ip_address    INET               NOT NULL,
    user_agent    VARCHAR(1000)      NOT NULL,
    created_at    TIMESTAMPTZ        NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ        NOT NULL DEFAULT now(),
    CONSTRAINT pk_password_reset_tokens PRIMARY KEY (id),
    CONSTRAINT uq_password_reset_tokens_hash UNIQUE (token_sha256),
    CONSTRAINT ck_password_reset_tokens_expiry CHECK (expires_at > requested_at),
    CONSTRAINT ck_password_reset_tokens_final_state CHECK (consumed_at IS NULL OR revoked_at IS NULL),
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id)
        REFERENCES auth.users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

-- Un solo token vivo por usuario: una solicitud nueva revoca el anterior.
CREATE UNIQUE INDEX IF NOT EXISTS uq_password_reset_tokens_user_live
    ON auth.password_reset_tokens (user_id) WHERE consumed_at IS NULL AND revoked_at IS NULL;
-- Límite de solicitudes por cuenta y hora (ADR-0014 §2).
CREATE INDEX IF NOT EXISTS ix_password_reset_tokens_user_requested
    ON auth.password_reset_tokens (user_id, requested_at);
SELECT common.attach_updated_at('auth.password_reset_tokens');

-- Material de autenticación: fuera del rol de reportes (igual que refresh_tokens y credential_activation_codes).
REVOKE ALL ON auth.password_reset_tokens FROM integrate_readonly;

COMMENT ON TABLE  auth.password_reset_tokens IS 'Tokens de restablecimiento de contraseña: un solo uso, 15 minutos de vigencia y como máximo 3 solicitudes por cuenta y hora (ADR-0014).';
COMMENT ON COLUMN auth.password_reset_tokens.token_sha256 IS 'SHA-256 del token opaco (32 bytes, Base64 URL). El token en claro solo viaja en el correo.';
COMMENT ON COLUMN auth.password_reset_tokens.revoked_at IS 'Fijado cuando una solicitud nueva reemplaza al token.';
COMMENT ON COLUMN auth.password_reset_tokens.ip_address IS 'Terminal que solicitó el restablecimiento (trazabilidad ante abuso).';
