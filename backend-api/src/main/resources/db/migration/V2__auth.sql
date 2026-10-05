-- =============================================================================
-- V2 · Módulo auth: identidades, RBAC N:M, políticas y consentimientos
--      Habeas Data, intentos de login y refresh tokens.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- auth.users
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS auth.users (
    id              UUID                NOT NULL DEFAULT gen_random_uuid(),
    email           common.email        NOT NULL,
    password_hash   VARCHAR(255)        NOT NULL,
    full_name       VARCHAR(150)        NOT NULL,
    doc_type        common.doc_type     NOT NULL,
    doc_number      common.doc_number   NOT NULL,
    phone           common.phone_e164   NOT NULL,
    status          VARCHAR(20)         NOT NULL DEFAULT 'ACTIVE',
    locked_until    TIMESTAMPTZ,
    last_login_at   TIMESTAMPTZ,
    anonymized_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ         NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ         NOT NULL DEFAULT now(),
    created_by      UUID                DEFAULT common.current_actor(),
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_document UNIQUE (doc_type, doc_number),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'ANONYMIZED')),
    CONSTRAINT ck_users_password_hash CHECK (password_hash ~ '^\$(2[aby]|argon2id)\$'),
    CONSTRAINT ck_users_full_name CHECK (length(btrim(full_name)) >= 3),
    CONSTRAINT ck_users_anonymized CHECK ((status = 'ANONYMIZED') = (anonymized_at IS NOT NULL)),
    CONSTRAINT fk_users_created_by FOREIGN KEY (created_by)
        REFERENCES auth.users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_email_lower ON auth.users (lower(email));
CREATE INDEX IF NOT EXISTS ix_users_status ON auth.users (status) WHERE deleted_at IS NULL;
SELECT common.attach_updated_at('auth.users');

COMMENT ON TABLE  auth.users IS 'Identidad única de cualquier actor humano (turista, anfitrión, gestor de enlace, administrador). Los roles se asignan en auth.user_roles.';
COMMENT ON COLUMN auth.users.id IS 'Identificador público (UUID) usado como subject del JWT de sesión.';
COMMENT ON COLUMN auth.users.email IS 'Correo de inicio de sesión; único sin distinguir mayúsculas (índice uq_users_email_lower).';
COMMENT ON COLUMN auth.users.password_hash IS 'Hash BCrypt (costo >= 12) o Argon2id (RNF-01). Nunca la contraseña en claro.';
COMMENT ON COLUMN auth.users.doc_type IS 'Tipo de documento (B2); obligatorio para todos los roles por trazabilidad ante la SIC.';
COMMENT ON COLUMN auth.users.doc_number IS 'Número de documento en claro (B3: dato personal, no sensible). Único junto con doc_type.';
COMMENT ON COLUMN auth.users.status IS 'ACTIVE | SUSPENDED (RF-03, suspensión por el admin) | ANONYMIZED (supresión Ley 1581).';
COMMENT ON COLUMN auth.users.locked_until IS 'Bloqueo temporal por 5 intentos fallidos en 10 minutos (RF-01, HTTP 423).';
COMMENT ON COLUMN auth.users.anonymized_at IS 'Fecha en que se reemplazaron los datos personales por valores anónimos (ver auth.anonymize_user).';
COMMENT ON COLUMN auth.users.created_by IS 'Usuario que creó el registro (NULL en el autorregistro).';
COMMENT ON COLUMN auth.users.deleted_at IS 'Baja lógica (A4). La fila se conserva por la retención legal de 10 años.';

-- -----------------------------------------------------------------------------
-- auth.roles (catálogo gobernado por migraciones)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS auth.roles (
    id           SMALLINT     GENERATED ALWAYS AS IDENTITY,
    code         VARCHAR(30)  NOT NULL,
    name         VARCHAR(80)  NOT NULL,
    description  TEXT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT uq_roles_code UNIQUE (code),
    CONSTRAINT ck_roles_code CHECK (code ~ '^[A-Z_]{3,30}$')
);
SELECT common.attach_updated_at('auth.roles');

INSERT INTO auth.roles (code, name, description) VALUES
    ('TOURIST',        'Turista',                    'Consulta el catálogo, reserva, paga y recibe vouchers.'),
    ('COMMUNITY_HOST', 'Anfitrión comunitario',      'Prestador local titular y beneficiario de experiencias.'),
    ('LINK_MANAGER',   'Gestor de enlace comunitario','Proxy digital acreditado por la asociación; valida vouchers en campo.'),
    ('ADMIN',          'Administrador de plataforma','Modera el catálogo, gestiona roles, auditoría y conciliación.')
ON CONFLICT (code) DO NOTHING;

COMMENT ON TABLE  auth.roles IS 'Catálogo de roles RBAC. Se modifica solo mediante migraciones (sin INSERT/UPDATE desde la app).';
COMMENT ON COLUMN auth.roles.code IS 'Código estable usado en los claims del JWT (ROLE_<code>).';

-- -----------------------------------------------------------------------------
-- auth.user_roles (N:M usuario-rol con historial de revocación; B1)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS auth.user_roles (
    id          BIGINT       GENERATED ALWAYS AS IDENTITY,
    user_id     UUID         NOT NULL,
    role_id     SMALLINT     NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  UUID         DEFAULT common.current_actor(),
    revoked_at  TIMESTAMPTZ,
    revoked_by  UUID,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_user_roles PRIMARY KEY (id),
    CONSTRAINT ck_user_roles_revocation CHECK (revoked_at IS NULL OR revoked_at >= created_at),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id)
        REFERENCES auth.users (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id)
        REFERENCES auth.roles (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_user_roles_created_by FOREIGN KEY (created_by)
        REFERENCES auth.users (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_user_roles_revoked_by FOREIGN KEY (revoked_by)
        REFERENCES auth.users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_user_roles_active
    ON auth.user_roles (user_id, role_id) WHERE revoked_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_user_roles_role ON auth.user_roles (role_id) WHERE revoked_at IS NULL;
SELECT common.attach_updated_at('auth.user_roles');

COMMENT ON TABLE  auth.user_roles IS 'Asignación N:M de roles a usuarios. Revocar = poner revoked_at; nunca se borra (trazabilidad de privilegios, RF-03).';
COMMENT ON COLUMN auth.user_roles.revoked_at IS 'Fin de vigencia del rol. Solo puede haber una asignación vigente por (usuario, rol).';

-- -----------------------------------------------------------------------------
-- auth.privacy_policies (versiones de la política de tratamiento de datos; B6)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS auth.privacy_policies (
    id              SMALLINT            GENERATED ALWAYS AS IDENTITY,
    version         VARCHAR(20)         NOT NULL,
    title           VARCHAR(200)        NOT NULL,
    document_url    VARCHAR(500)        NOT NULL,
    content_sha256  common.sha256_hex   NOT NULL,
    effective_from  TIMESTAMPTZ         NOT NULL,
    is_current      BOOLEAN             NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ         NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ         NOT NULL DEFAULT now(),
    created_by      UUID                DEFAULT common.current_actor(),
    CONSTRAINT pk_privacy_policies PRIMARY KEY (id),
    CONSTRAINT uq_privacy_policies_version UNIQUE (version),
    CONSTRAINT ck_privacy_policies_version CHECK (version ~ '^v[0-9]+\.[0-9]+$'),
    CONSTRAINT ck_privacy_policies_url CHECK (document_url ~ '^https://'),
    CONSTRAINT fk_privacy_policies_created_by FOREIGN KEY (created_by)
        REFERENCES auth.users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_privacy_policies_current
    ON auth.privacy_policies (is_current) WHERE is_current;
SELECT common.attach_updated_at('auth.privacy_policies');

-- El texto publicado de una política es inmutable: solo puede cambiar is_current.
CREATE OR REPLACE FUNCTION auth.fn_privacy_policy_freeze()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF ROW(NEW.version, NEW.title, NEW.document_url, NEW.content_sha256, NEW.effective_from)
       IS DISTINCT FROM
       ROW(OLD.version, OLD.title, OLD.document_url, OLD.content_sha256, OLD.effective_from) THEN
        RAISE EXCEPTION 'La política % ya fue publicada; cree una nueva versión', OLD.version
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE TRIGGER trg_privacy_policy_freeze
    BEFORE UPDATE ON auth.privacy_policies
    FOR EACH ROW EXECUTE FUNCTION auth.fn_privacy_policy_freeze();

COMMENT ON TABLE  auth.privacy_policies IS 'Versiones publicadas de la política de tratamiento de datos (Ley 1581/2012, Decreto 1377/2013). Su contenido es inmutable.';
COMMENT ON COLUMN auth.privacy_policies.content_sha256 IS 'Hash del texto exacto mostrado al titular: prueba de qué aceptó.';
COMMENT ON COLUMN auth.privacy_policies.is_current IS 'Única versión vigente. Si el usuario no la aceptó, debe revalidar antes de transar (RF-02).';

-- -----------------------------------------------------------------------------
-- auth.habeas_data_consents (append-only; B7)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS auth.habeas_data_consents (
    id           BIGINT        GENERATED ALWAYS AS IDENTITY,
    user_id      UUID          NOT NULL,
    policy_id    SMALLINT      NOT NULL,
    purpose      VARCHAR(30)   NOT NULL,
    action       VARCHAR(10)   NOT NULL,
    ip_address   INET          NOT NULL,
    user_agent   VARCHAR(1000) NOT NULL,
    occurred_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_habeas_data_consents PRIMARY KEY (id),
    CONSTRAINT ck_consents_purpose CHECK (purpose IN ('PERSONAL_DATA', 'SENSITIVE_VICTIM_DATA', 'HEALTH_DATA')),
    CONSTRAINT ck_consents_action CHECK (action IN ('GRANTED', 'REVOKED')),
    CONSTRAINT fk_consents_user FOREIGN KEY (user_id)
        REFERENCES auth.users (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_consents_policy FOREIGN KEY (policy_id)
        REFERENCES auth.privacy_policies (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX IF NOT EXISTS ix_consents_user_purpose
    ON auth.habeas_data_consents (user_id, purpose, occurred_at DESC);
CREATE INDEX IF NOT EXISTS ix_consents_policy ON auth.habeas_data_consents (policy_id);
SELECT common.make_append_only('auth.habeas_data_consents');

COMMENT ON TABLE  auth.habeas_data_consents IS 'Traza inmutable de otorgamiento y revocación de consentimiento por finalidad (RF-02). Solo inserción; retención mínima de 10 años (RNF-09).';
COMMENT ON COLUMN auth.habeas_data_consents.purpose IS 'PERSONAL_DATA (todos) | SENSITIVE_VICTIM_DATA (anfitriones) | HEALTH_DATA (turistas: EPS y contacto de emergencia).';
COMMENT ON COLUMN auth.habeas_data_consents.action IS 'GRANTED al aceptar; REVOKED cuando el titular retira el consentimiento (art. 9 Ley 1581).';
COMMENT ON COLUMN auth.habeas_data_consents.occurred_at IS 'Marca UTC del acto de consentimiento.';

CREATE OR REPLACE VIEW auth.v_current_consents AS
SELECT DISTINCT ON (c.user_id, c.purpose)
       c.user_id,
       c.purpose,
       c.action,
       c.policy_id,
       p.version                               AS policy_version,
       c.occurred_at,
       (c.action = 'GRANTED' AND p.is_current) AS is_valid_for_current_policy
  FROM auth.habeas_data_consents c
  JOIN auth.privacy_policies p ON p.id = c.policy_id
 ORDER BY c.user_id, c.purpose, c.occurred_at DESC, c.id DESC;

COMMENT ON VIEW auth.v_current_consents IS 'Último acto de consentimiento por usuario y finalidad; indica si cubre la política vigente.';

-- -----------------------------------------------------------------------------
-- auth.login_attempts (bloqueo 5 intentos / 10 min; B8)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS auth.login_attempts (
    id               BIGINT         GENERATED ALWAYS AS IDENTITY,
    user_id          UUID,
    email_attempted  VARCHAR(254)   NOT NULL,
    ip_address       INET           NOT NULL,
    user_agent       VARCHAR(1000),
    succeeded        BOOLEAN        NOT NULL,
    failure_reason   VARCHAR(30),
    attempted_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT pk_login_attempts PRIMARY KEY (id),
    CONSTRAINT ck_login_attempts_reason CHECK (
        (succeeded AND failure_reason IS NULL) OR
        (NOT succeeded AND failure_reason IN ('BAD_CREDENTIALS', 'ACCOUNT_LOCKED', 'ACCOUNT_SUSPENDED', 'UNKNOWN_USER'))
    ),
    CONSTRAINT fk_login_attempts_user FOREIGN KEY (user_id)
        REFERENCES auth.users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX IF NOT EXISTS ix_login_attempts_user_failed
    ON auth.login_attempts (user_id, attempted_at DESC) WHERE NOT succeeded;
CREATE INDEX IF NOT EXISTS ix_login_attempts_ip ON auth.login_attempts (ip_address, attempted_at DESC);
CREATE INDEX IF NOT EXISTS ix_login_attempts_attempted_at ON auth.login_attempts (attempted_at);

COMMENT ON TABLE  auth.login_attempts IS 'Registro de intentos de autenticación para el bloqueo temporal (RF-01) y detección de fuerza bruta. Tabla operativa: admite depuración física (> 1 año).';
COMMENT ON COLUMN auth.login_attempts.user_id IS 'NULL cuando el correo no corresponde a ningún usuario.';

-- -----------------------------------------------------------------------------
-- auth.refresh_tokens (sesiones revocables con rotación; B8)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS auth.refresh_tokens (
    id              UUID               NOT NULL DEFAULT gen_random_uuid(),
    user_id         UUID               NOT NULL,
    token_sha256    common.sha256_hex  NOT NULL,
    issued_at       TIMESTAMPTZ        NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ        NOT NULL,
    revoked_at      TIMESTAMPTZ,
    revoke_reason   VARCHAR(30),
    replaced_by_id  UUID,
    ip_address      INET,
    user_agent      VARCHAR(1000),
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_sha256),
    CONSTRAINT ck_refresh_tokens_expiry CHECK (expires_at > issued_at),
    CONSTRAINT ck_refresh_tokens_revoke CHECK (
        (revoked_at IS NULL AND revoke_reason IS NULL) OR
        (revoked_at IS NOT NULL AND revoke_reason IN ('LOGOUT', 'ROTATED', 'REUSE_DETECTED', 'ADMIN', 'PASSWORD_CHANGE'))
    ),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id)
        REFERENCES auth.users (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_refresh_tokens_replaced_by FOREIGN KEY (replaced_by_id)
        REFERENCES auth.refresh_tokens (id) ON DELETE SET NULL ON UPDATE RESTRICT
);

CREATE INDEX IF NOT EXISTS ix_refresh_tokens_user_active
    ON auth.refresh_tokens (user_id) WHERE revoked_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_refresh_tokens_expires_at ON auth.refresh_tokens (expires_at);
CREATE INDEX IF NOT EXISTS ix_refresh_tokens_replaced_by ON auth.refresh_tokens (replaced_by_id);

COMMENT ON TABLE  auth.refresh_tokens IS 'Refresh tokens emitidos. Solo se guarda su hash; la reutilización de un token rotado revoca la familia. Tabla operativa: depuración física tras expirar.';
COMMENT ON COLUMN auth.refresh_tokens.token_sha256 IS 'SHA-256 del token opaco entregado al cliente; el token en claro nunca se persiste.';
COMMENT ON COLUMN auth.refresh_tokens.replaced_by_id IS 'Token que reemplazó a este en la rotación.';

-- -----------------------------------------------------------------------------
-- Supresión de datos (Ley 1581, art. 8 e): anonimiza conservando la fila para
-- las relaciones financieras y la retención legal (A4).
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION auth.anonymize_user(p_user_id UUID)
RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
    UPDATE auth.users
       SET email         = 'anon+' || id::text || '@anonymized.invalid',
           password_hash = '$2a$12$anonymized.account.cannot.login',
           full_name     = 'Titular anonimizado',
           doc_type      = 'PA',
           doc_number    = 'ANON' || left(replace(id::text, '-', ''), 16),
           phone         = '+10000000000',
           status        = 'ANONYMIZED',
           anonymized_at = now(),
           deleted_at    = COALESCE(deleted_at, now())
     WHERE id = p_user_id
       AND status <> 'ANONYMIZED';

    UPDATE auth.refresh_tokens
       SET revoked_at = now(), revoke_reason = 'ADMIN'
     WHERE user_id = p_user_id AND revoked_at IS NULL;
END;
$$;

COMMENT ON FUNCTION auth.anonymize_user(UUID) IS
    'Reemplaza los datos personales del usuario por valores anónimos y revoca sus sesiones. Los datos sensibles cifrados de catalog.host_profiles se eliminan en V3 (catalog.anonymize_host_sensitive_data).';
