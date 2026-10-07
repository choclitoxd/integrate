-- =============================================================================
-- V12 · Activación asistida de credenciales (ADR-0007 §6, T-1.3).
--
-- Un anfitrión enrolado por un gestor nace con credential_status = PENDING_RESET
-- y no puede iniciar sesión. El gestor emite un código de un solo uso que el
-- anfitrión canjea por su propia contraseña (POST /api/v1/auth/activate).
-- =============================================================================

CREATE TABLE IF NOT EXISTS auth.credential_activation_codes (
    id               BIGINT             GENERATED ALWAYS AS IDENTITY,
    user_id          UUID               NOT NULL,
    code_sha256      common.sha256_hex  NOT NULL,
    issued_by        UUID               NOT NULL,
    issued_at        TIMESTAMPTZ        NOT NULL,
    expires_at       TIMESTAMPTZ        NOT NULL,
    failed_attempts  SMALLINT           NOT NULL DEFAULT 0,
    consumed_at      TIMESTAMPTZ,
    revoked_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ        NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ        NOT NULL DEFAULT now(),
    CONSTRAINT pk_credential_activation_codes PRIMARY KEY (id),
    CONSTRAINT ck_activation_codes_expiry CHECK (expires_at > issued_at),
    CONSTRAINT ck_activation_codes_attempts CHECK (failed_attempts BETWEEN 0 AND 5),
    CONSTRAINT ck_activation_codes_final_state CHECK (consumed_at IS NULL OR revoked_at IS NULL),
    CONSTRAINT fk_activation_codes_user FOREIGN KEY (user_id)
        REFERENCES auth.users (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_activation_codes_issued_by FOREIGN KEY (issued_by)
        REFERENCES auth.users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

-- Un solo código vivo por usuario: emitir uno nuevo revoca el anterior.
CREATE UNIQUE INDEX IF NOT EXISTS uq_activation_codes_user_live
    ON auth.credential_activation_codes (user_id) WHERE consumed_at IS NULL AND revoked_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_activation_codes_issued_by ON auth.credential_activation_codes (issued_by);
SELECT common.attach_updated_at('auth.credential_activation_codes');

-- Los hashes de códigos son material de autenticación: fuera del rol de reportes (igual que refresh_tokens, V9).
REVOKE ALL ON auth.credential_activation_codes FROM integrate_readonly;

COMMENT ON TABLE  auth.credential_activation_codes IS 'Códigos de un solo uso para activar la cuenta de un anfitrión registrado por un gestor (PENDING_RESET → ACTIVE). Vencen a las 72 h; 5 intentos fallidos los invalidan.';
COMMENT ON COLUMN auth.credential_activation_codes.code_sha256 IS 'SHA-256 del código normalizado; el código en claro se muestra una sola vez al gestor y nunca se persiste.';
COMMENT ON COLUMN auth.credential_activation_codes.issued_by IS 'Gestor de enlace que emitió el código.';
COMMENT ON COLUMN auth.credential_activation_codes.revoked_at IS 'Fijado al emitir un código nuevo o al agotar los intentos.';
