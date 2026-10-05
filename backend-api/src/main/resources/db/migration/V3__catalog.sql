-- =============================================================================
-- V3 · Módulo catalog: DIVIPOLA, asociaciones, anfitriones, gestores de enlace,
--      asignaciones gestor-anfitrión, categorías, experiencias y franjas.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- catalog.departments / catalog.municipalities (DIVIPOLA - DANE; C6)
-- Clave natural oficial: el código DANE es estable, corto y de dominio público.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS catalog.departments (
    code        CHAR(2)       NOT NULL,
    name        VARCHAR(80)   NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_departments PRIMARY KEY (code),
    CONSTRAINT uq_departments_name UNIQUE (name),
    CONSTRAINT ck_departments_code CHECK (code ~ '^[0-9]{2}$')
);

CREATE TABLE IF NOT EXISTS catalog.municipalities (
    code             CHAR(5)       NOT NULL,
    department_code  CHAR(2)       NOT NULL,
    name             VARCHAR(100)  NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_municipalities PRIMARY KEY (code),
    CONSTRAINT uq_municipalities_name UNIQUE (department_code, name),
    CONSTRAINT ck_municipalities_code CHECK (code ~ '^[0-9]{5}$' AND left(code, 2) = department_code),
    CONSTRAINT fk_municipalities_department FOREIGN KEY (department_code)
        REFERENCES catalog.departments (code) ON DELETE RESTRICT ON UPDATE RESTRICT
);

INSERT INTO catalog.departments (code, name) VALUES
    ('05', 'Antioquia'), ('08', 'Atlántico'), ('11', 'Bogotá, D.C.'), ('13', 'Bolívar'),
    ('15', 'Boyacá'), ('17', 'Caldas'), ('18', 'Caquetá'), ('19', 'Cauca'), ('20', 'Cesar'),
    ('23', 'Córdoba'), ('25', 'Cundinamarca'), ('27', 'Chocó'), ('41', 'Huila'),
    ('44', 'La Guajira'), ('47', 'Magdalena'), ('50', 'Meta'), ('52', 'Nariño'),
    ('54', 'Norte de Santander'), ('63', 'Quindío'), ('66', 'Risaralda'), ('68', 'Santander'),
    ('70', 'Sucre'), ('73', 'Tolima'), ('76', 'Valle del Cauca'), ('81', 'Arauca'),
    ('85', 'Casanare'), ('86', 'Putumayo'),
    ('88', 'Archipiélago de San Andrés, Providencia y Santa Catalina'),
    ('91', 'Amazonas'), ('94', 'Guainía'), ('95', 'Guaviare'), ('97', 'Vaupés'), ('99', 'Vichada')
ON CONFLICT (code) DO NOTHING;

COMMENT ON TABLE catalog.departments IS 'Departamentos de Colombia según DIVIPOLA (DANE). Datos de referencia cargados por migración.';
COMMENT ON TABLE catalog.municipalities IS 'Municipios DIVIPOLA. Se cargan con una migración de datos a partir del archivo oficial del DANE (no incluida aquí).';
COMMENT ON COLUMN catalog.municipalities.code IS 'Código DANE de 5 dígitos; sus 2 primeros dígitos son el departamento.';

-- -----------------------------------------------------------------------------
-- catalog.community_associations (titular de NIT y RNT sombrilla; C1)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS catalog.community_associations (
    id                 UUID               NOT NULL DEFAULT gen_random_uuid(),
    nit                VARCHAR(15)        NOT NULL,
    rnt_number         VARCHAR(20)        NOT NULL,
    legal_name         VARCHAR(200)       NOT NULL,
    association_type   VARCHAR(30)        NOT NULL,
    municipality_code  CHAR(5)            NOT NULL,
    address            VARCHAR(250),
    contact_email      common.email       NOT NULL,
    contact_phone      common.phone_e164  NOT NULL,
    status             VARCHAR(20)        NOT NULL DEFAULT 'ACTIVE',
    created_at         TIMESTAMPTZ        NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ        NOT NULL DEFAULT now(),
    created_by         UUID               DEFAULT common.current_actor(),
    deleted_at         TIMESTAMPTZ,
    CONSTRAINT pk_community_associations PRIMARY KEY (id),
    CONSTRAINT uq_associations_nit UNIQUE (nit),
    CONSTRAINT uq_associations_rnt UNIQUE (rnt_number),
    CONSTRAINT ck_associations_nit CHECK (nit ~ '^[0-9]{6,12}-[0-9]$'),
    CONSTRAINT ck_associations_rnt CHECK (rnt_number ~ '^[0-9]{3,20}$'),
    CONSTRAINT ck_associations_type CHECK (association_type IN
        ('JUNTA_ACCION_COMUNAL', 'COOPERATIVA_CAMPESINA', 'ASOCIACION_VICTIMAS', 'OTRA')),
    CONSTRAINT ck_associations_status CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    CONSTRAINT fk_associations_municipality FOREIGN KEY (municipality_code)
        REFERENCES catalog.municipalities (code) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_associations_created_by FOREIGN KEY (created_by)
        REFERENCES auth.users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX IF NOT EXISTS ix_associations_municipality ON catalog.community_associations (municipality_code);
SELECT common.attach_updated_at('catalog.community_associations');

COMMENT ON TABLE  catalog.community_associations IS 'Asociación comunitaria (JAC, cooperativa o asociación de víctimas). Para la BD, "comunidad" y "asociación" son la misma entidad jurídica. Recibe el 85% de la dispersión.';
COMMENT ON COLUMN catalog.community_associations.nit IS 'NIT con dígito de verificación (formato 900123456-7).';
COMMENT ON COLUMN catalog.community_associations.rnt_number IS 'Registro Nacional de Turismo "sombrilla" que ampara a los anfitriones.';
COMMENT ON COLUMN catalog.community_associations.contact_email IS 'Correo al que se envían los comprobantes de dispersión (RF-19).';

-- -----------------------------------------------------------------------------
-- catalog.host_profiles (anfitrión: N:1 con asociación; B5, C2)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS catalog.host_profiles (
    id                           UUID               NOT NULL DEFAULT gen_random_uuid(),
    user_id                      UUID               NOT NULL,
    association_id               UUID               NOT NULL,
    victim_condition_enc         common.ciphertext,
    ruv_registration_number_enc  common.ciphertext,
    victimization_type_enc       common.ciphertext,
    support_doc_s3_key           common.s3_key,
    sensitive_key_version        SMALLINT,
    is_verified                  BOOLEAN            NOT NULL DEFAULT FALSE,
    verified_at                  TIMESTAMPTZ,
    verified_by                  UUID,
    created_at                   TIMESTAMPTZ        NOT NULL DEFAULT now(),
    updated_at                   TIMESTAMPTZ        NOT NULL DEFAULT now(),
    created_by                   UUID               DEFAULT common.current_actor(),
    deleted_at                   TIMESTAMPTZ,
    CONSTRAINT pk_host_profiles PRIMARY KEY (id),
    CONSTRAINT uq_host_profiles_id_association UNIQUE (id, association_id),
    CONSTRAINT ck_host_profiles_victim_detail CHECK (
        victim_condition_enc IS NOT NULL
        OR (ruv_registration_number_enc IS NULL AND victimization_type_enc IS NULL AND support_doc_s3_key IS NULL)
    ),
    CONSTRAINT ck_host_profiles_key_version CHECK (
        (victim_condition_enc IS NULL AND sensitive_key_version IS NULL)
        OR (victim_condition_enc IS NOT NULL AND sensitive_key_version IS NOT NULL AND sensitive_key_version > 0)
    ),
    CONSTRAINT ck_host_profiles_verified CHECK (is_verified = (verified_at IS NOT NULL)),
    CONSTRAINT fk_host_profiles_user FOREIGN KEY (user_id)
        REFERENCES auth.users (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_host_profiles_association FOREIGN KEY (association_id)
        REFERENCES catalog.community_associations (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_host_profiles_verified_by FOREIGN KEY (verified_by)
        REFERENCES auth.users (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_host_profiles_created_by FOREIGN KEY (created_by)
        REFERENCES auth.users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

-- Un usuario tiene a lo sumo un perfil de anfitrión vigente. Cambiar de asociación
-- = dar de baja el perfil y crear otro (preserva el historial de experiencias y asignaciones).
CREATE UNIQUE INDEX IF NOT EXISTS uq_host_profiles_user_active
    ON catalog.host_profiles (user_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_host_profiles_user ON catalog.host_profiles (user_id);
CREATE INDEX IF NOT EXISTS ix_host_profiles_association ON catalog.host_profiles (association_id);
SELECT common.attach_updated_at('catalog.host_profiles');

COMMENT ON TABLE  catalog.host_profiles IS 'Perfil del anfitrión comunitario: prestador de la experiencia. Pertenece a una sola asociación (N:1). Contiene datos sensibles de víctima cifrados (Ley 1448/2011, Ley 1581/2012).';
COMMENT ON COLUMN catalog.host_profiles.victim_condition_enc IS 'Atributo de dominio isVictimProtected (boolean) cifrado con AES-256-GCM. La condición de víctima es dato sensible (B4, R-05). NULL = no declarada.';
COMMENT ON COLUMN catalog.host_profiles.ruv_registration_number_enc IS 'Número de inscripción en el Registro Único de Víctimas, cifrado.';
COMMENT ON COLUMN catalog.host_profiles.victimization_type_enc IS 'Hecho victimizante, cifrado (revela la condición de víctima, por lo tanto es sensible).';
COMMENT ON COLUMN catalog.host_profiles.support_doc_s3_key IS 'Clave S3 del documento soporte (bucket con SSE-KMS). La clave no debe contener nombres ni documentos.';
COMMENT ON COLUMN catalog.host_profiles.sensitive_key_version IS 'Versión de la llave AES con que se cifraron las columnas *_enc de la fila (rotación de llaves).';

-- -----------------------------------------------------------------------------
-- catalog.link_manager_profiles (gestor de enlace: N:1 con asociación; C2, C4)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS catalog.link_manager_profiles (
    id                         UUID           NOT NULL DEFAULT gen_random_uuid(),
    user_id                    UUID           NOT NULL,
    association_id             UUID           NOT NULL,
    accreditation_status       VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    accredited_on              DATE           NOT NULL,
    accreditation_doc_s3_key   common.s3_key,
    accredited_by              UUID,
    created_at                 TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by                 UUID           DEFAULT common.current_actor(),
    deleted_at                 TIMESTAMPTZ,
    CONSTRAINT pk_link_manager_profiles PRIMARY KEY (id),
    CONSTRAINT uq_link_manager_profiles_id_association UNIQUE (id, association_id),
    CONSTRAINT ck_link_manager_status CHECK (accreditation_status IN ('ACTIVE', 'SUSPENDED', 'REVOKED')),
    CONSTRAINT fk_link_manager_user FOREIGN KEY (user_id)
        REFERENCES auth.users (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_link_manager_association FOREIGN KEY (association_id)
        REFERENCES catalog.community_associations (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_link_manager_accredited_by FOREIGN KEY (accredited_by)
        REFERENCES auth.users (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_link_manager_created_by FOREIGN KEY (created_by)
        REFERENCES auth.users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_link_manager_user_active
    ON catalog.link_manager_profiles (user_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_link_manager_user ON catalog.link_manager_profiles (user_id);
CREATE INDEX IF NOT EXISTS ix_link_manager_association ON catalog.link_manager_profiles (association_id);
SELECT common.attach_updated_at('catalog.link_manager_profiles');

COMMENT ON TABLE  catalog.link_manager_profiles IS 'Acreditación de un gestor de enlace (proxy digital) ante una única asociación a la vez. Cambiar de asociación = nueva acreditación.';
COMMENT ON COLUMN catalog.link_manager_profiles.accreditation_status IS 'ACTIVE | SUSPENDED | REVOKED. Solo un gestor ACTIVE puede descargar padrones y validar vouchers.';

-- -----------------------------------------------------------------------------
-- catalog.host_manager_assignments (historial gestor ↔ anfitrión; C3, RF-03)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS catalog.host_manager_assignments (
    id              BIGINT         GENERATED ALWAYS AS IDENTITY,
    host_id         UUID           NOT NULL,
    manager_id      UUID           NOT NULL,
    association_id  UUID           NOT NULL,
    valid_from      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    valid_to        TIMESTAMPTZ,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by      UUID           DEFAULT common.current_actor(),
    CONSTRAINT pk_host_manager_assignments PRIMARY KEY (id),
    CONSTRAINT ck_hma_period CHECK (valid_to IS NULL OR valid_to > valid_from),
    -- RF-03: gestor y anfitrión deben pertenecer a la misma asociación (FK compuestas).
    CONSTRAINT fk_hma_host_same_association FOREIGN KEY (host_id, association_id)
        REFERENCES catalog.host_profiles (id, association_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_hma_manager_same_association FOREIGN KEY (manager_id, association_id)
        REFERENCES catalog.link_manager_profiles (id, association_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_hma_created_by FOREIGN KEY (created_by)
        REFERENCES auth.users (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    -- Un anfitrión no puede tener dos gestores en periodos que se solapen.
    CONSTRAINT ex_hma_no_overlap EXCLUDE USING gist (
        host_id WITH =,
        tstzrange(valid_from, valid_to, '[)') WITH &&
    )
);

CREATE INDEX IF NOT EXISTS ix_hma_manager_active
    ON catalog.host_manager_assignments (manager_id) WHERE valid_to IS NULL;
CREATE INDEX IF NOT EXISTS ix_hma_manager ON catalog.host_manager_assignments (manager_id, association_id);
CREATE INDEX IF NOT EXISTS ix_hma_host ON catalog.host_manager_assignments (host_id, association_id);
SELECT common.attach_updated_at('catalog.host_manager_assignments');

COMMENT ON TABLE  catalog.host_manager_assignments IS 'Jurisdicción del gestor sobre anfitriones, con vigencia. Asignación vigente = valid_to IS NULL. Lo administra solo ROLE_ADMIN (RF-03).';
COMMENT ON COLUMN catalog.host_manager_assignments.association_id IS 'Copia controlada de la asociación común a ambos perfiles. Existe solo para que las FK compuestas garanticen la regla de misma asociación.';

-- -----------------------------------------------------------------------------
-- catalog.categories (N:M con experiencias; D9)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS catalog.categories (
    id           SMALLINT      GENERATED ALWAYS AS IDENTITY,
    code         VARCHAR(40)   NOT NULL,
    name         VARCHAR(80)   NOT NULL,
    description  TEXT,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by   UUID          DEFAULT common.current_actor(),
    deleted_at   TIMESTAMPTZ,
    CONSTRAINT pk_categories PRIMARY KEY (id),
    CONSTRAINT uq_categories_code UNIQUE (code),
    CONSTRAINT ck_categories_code CHECK (code ~ '^[A-Z_]{3,40}$'),
    CONSTRAINT fk_categories_created_by FOREIGN KEY (created_by)
        REFERENCES auth.users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);
SELECT common.attach_updated_at('catalog.categories');

INSERT INTO catalog.categories (code, name, description) VALUES
    ('AGROTURISMO',             'Agroturismo',                 'Experiencias en fincas y procesos productivos campesinos.'),
    ('SENDERISMO_MEMORIA',      'Senderismo de memoria',       'Recorridos guiados por lugares de memoria histórica del conflicto.'),
    ('GASTRONOMIA_TRADICIONAL', 'Gastronomía tradicional',     'Cocina y saberes culinarios de la comunidad.'),
    ('TALLER_MEMORIA',          'Talleres de memoria',         'Espacios de diálogo, arte y reconstrucción de memoria.'),
    ('NATURALEZA_CONSERVACION', 'Naturaleza y conservación',   'Avistamiento, reservas ecológicas y senderos de conservación.')
ON CONFLICT (code) DO NOTHING;

COMMENT ON TABLE catalog.categories IS 'Categorías temáticas del catálogo territorial.';

-- -----------------------------------------------------------------------------
-- catalog.experiences
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS catalog.experiences (
    id                     UUID                NOT NULL DEFAULT gen_random_uuid(),
    host_id                UUID                NOT NULL,
    title                  VARCHAR(200)        NOT NULL,
    description            TEXT                NOT NULL,
    safety_guidelines      TEXT                NOT NULL,
    physical_intensity     VARCHAR(10)         NOT NULL,
    duration_minutes       INTEGER             NOT NULL,
    municipality_code      CHAR(5)             NOT NULL,
    vereda                 VARCHAR(150),
    meeting_point          VARCHAR(500)        NOT NULL,
    latitude               NUMERIC(8, 6),
    longitude              NUMERIC(9, 6),
    max_capacity_per_slot  SMALLINT            NOT NULL,
    unit_price_cop         common.cop_amount   NOT NULL,
    timezone               VARCHAR(40)         NOT NULL DEFAULT 'America/Bogota',
    status                 VARCHAR(20)         NOT NULL DEFAULT 'DRAFT',
    rejection_reason       TEXT,
    created_at             TIMESTAMPTZ         NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ         NOT NULL DEFAULT now(),
    created_by             UUID                NOT NULL DEFAULT common.current_actor(),
    deleted_at             TIMESTAMPTZ,
    CONSTRAINT pk_experiences PRIMARY KEY (id),
    CONSTRAINT ck_experiences_title CHECK (length(btrim(title)) >= 5),
    CONSTRAINT ck_experiences_intensity CHECK (physical_intensity IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_experiences_duration CHECK (duration_minutes BETWEEN 15 AND 4320),
    CONSTRAINT ck_experiences_capacity CHECK (max_capacity_per_slot BETWEEN 1 AND 30),
    CONSTRAINT ck_experiences_price CHECK (unit_price_cop > 0),
    CONSTRAINT ck_experiences_coordinates CHECK (
        (latitude IS NULL AND longitude IS NULL) OR
        (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180)
    ),
    CONSTRAINT ck_experiences_status CHECK (status IN
        ('DRAFT', 'PENDING_REVIEW', 'REJECTED', 'APPROVED', 'SUSPENDED', 'RETIRED')),
    CONSTRAINT ck_experiences_rejection CHECK (status <> 'REJECTED' OR rejection_reason IS NOT NULL),
    CONSTRAINT fk_experiences_host FOREIGN KEY (host_id)
        REFERENCES catalog.host_profiles (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_experiences_municipality FOREIGN KEY (municipality_code)
        REFERENCES catalog.municipalities (code) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_experiences_created_by FOREIGN KEY (created_by)
        REFERENCES auth.users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX IF NOT EXISTS ix_experiences_host ON catalog.experiences (host_id);
CREATE INDEX IF NOT EXISTS ix_experiences_municipality ON catalog.experiences (municipality_code);
CREATE INDEX IF NOT EXISTS ix_experiences_created_by ON catalog.experiences (created_by);
CREATE INDEX IF NOT EXISTS ix_experiences_public_catalog
    ON catalog.experiences (municipality_code, physical_intensity)
    WHERE status = 'APPROVED' AND deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_experiences_review_queue
    ON catalog.experiences (created_at) WHERE status = 'PENDING_REVIEW';
CREATE INDEX IF NOT EXISTS ix_experiences_title_trgm
    ON catalog.experiences USING gin (title gin_trgm_ops);
SELECT common.attach_updated_at('catalog.experiences');

COMMENT ON TABLE  catalog.experiences IS 'Experiencia turística comunitaria. Solo las APPROVED se publican (RF-04, RF-07).';
COMMENT ON COLUMN catalog.experiences.host_id IS 'Anfitrión titular: prestador y beneficiario económico (D1).';
COMMENT ON COLUMN catalog.experiences.created_by IS 'Creador técnico del registro: el propio anfitrión (RF-05) o el gestor de enlace (RF-06). Obligatorio.';
COMMENT ON COLUMN catalog.experiences.max_capacity_per_slot IS 'Capacidad de carga ambiental máxima por franja (1–30, RF-05). Tope de schedules.total_slots.';
COMMENT ON COLUMN catalog.experiences.unit_price_cop IS 'Tarifa plana por persona en COP (D3). La reserva guarda una copia al reservar.';
COMMENT ON COLUMN catalog.experiences.timezone IS 'Zona horaria IANA para derivar la fecha local de la experiencia y del voucher (E7).';
COMMENT ON COLUMN catalog.experiences.status IS 'DRAFT → PENDING_REVIEW → APPROVED | REJECTED; APPROVED ↔ SUSPENDED; cualquiera → RETIRED. Historial en experience_status_history.';
COMMENT ON COLUMN catalog.experiences.rejection_reason IS 'Motivo del último rechazo, para retroalimentación a la comunidad (RF-07).';

-- -----------------------------------------------------------------------------
-- catalog.experience_categories (N:M)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS catalog.experience_categories (
    experience_id  UUID         NOT NULL,
    category_id    SMALLINT     NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by     UUID         DEFAULT common.current_actor(),
    CONSTRAINT pk_experience_categories PRIMARY KEY (experience_id, category_id),
    CONSTRAINT fk_exp_categories_experience FOREIGN KEY (experience_id)
        REFERENCES catalog.experiences (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_exp_categories_category FOREIGN KEY (category_id)
        REFERENCES catalog.categories (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_exp_categories_created_by FOREIGN KEY (created_by)
        REFERENCES auth.users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);
CREATE INDEX IF NOT EXISTS ix_exp_categories_category ON catalog.experience_categories (category_id);

COMMENT ON TABLE catalog.experience_categories IS 'Relación N:M entre experiencias y categorías. Tabla puente: es la única donde se permite DELETE físico (V9).';

-- -----------------------------------------------------------------------------
-- catalog.experience_images (1:N; D9)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS catalog.experience_images (
    id              BIGINT         GENERATED ALWAYS AS IDENTITY,
    experience_id   UUID           NOT NULL,
    s3_object_key   common.s3_key  NOT NULL,
    content_type    VARCHAR(30)    NOT NULL,
    alt_text        VARCHAR(300)   NOT NULL,
    sort_order      SMALLINT       NOT NULL DEFAULT 0,
    is_cover        BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by      UUID           DEFAULT common.current_actor(),
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT pk_experience_images PRIMARY KEY (id),
    CONSTRAINT uq_experience_images_key UNIQUE (s3_object_key),
    CONSTRAINT ck_experience_images_type CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT ck_experience_images_alt CHECK (length(btrim(alt_text)) >= 5),
    CONSTRAINT ck_experience_images_order CHECK (sort_order >= 0),
    CONSTRAINT fk_experience_images_experience FOREIGN KEY (experience_id)
        REFERENCES catalog.experiences (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_experience_images_created_by FOREIGN KEY (created_by)
        REFERENCES auth.users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_experience_images_cover
    ON catalog.experience_images (experience_id) WHERE is_cover AND deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_experience_images_order
    ON catalog.experience_images (experience_id, sort_order) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_experience_images_experience ON catalog.experience_images (experience_id);
SELECT common.attach_updated_at('catalog.experience_images');

COMMENT ON TABLE  catalog.experience_images IS 'Galería de imágenes de la experiencia almacenadas en S3.';
COMMENT ON COLUMN catalog.experience_images.alt_text IS 'Texto alternativo obligatorio (WCAG 2.1 AA / NTC 5854, RNF-07).';

-- -----------------------------------------------------------------------------
-- catalog.experience_status_history (append-only; D2)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS catalog.experience_status_history (
    id             BIGINT        GENERATED ALWAYS AS IDENTITY,
    experience_id  UUID          NOT NULL,
    from_status    VARCHAR(20),
    to_status      VARCHAR(20)   NOT NULL,
    reason         TEXT,
    changed_by     UUID,
    changed_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_experience_status_history PRIMARY KEY (id),
    CONSTRAINT fk_exp_status_history_experience FOREIGN KEY (experience_id)
        REFERENCES catalog.experiences (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_exp_status_history_changed_by FOREIGN KEY (changed_by)
        REFERENCES auth.users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);
CREATE INDEX IF NOT EXISTS ix_exp_status_history_experience
    ON catalog.experience_status_history (experience_id, changed_at DESC);
SELECT common.make_append_only('catalog.experience_status_history');

COMMENT ON TABLE catalog.experience_status_history IS 'Bitácora inmutable de transiciones de estado de cada experiencia (moderación, suspensiones). La alimenta un trigger.';

-- Reglas de la experiencia: transiciones válidas, zona horaria y reducción de aforo.
CREATE OR REPLACE FUNCTION catalog.fn_experience_guard()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    PERFORM now() AT TIME ZONE NEW.timezone;   -- falla si la zona horaria IANA no existe

    IF TG_OP = 'INSERT' THEN
        IF NEW.status NOT IN ('DRAFT', 'PENDING_REVIEW') THEN
            RAISE EXCEPTION 'Una experiencia nace en DRAFT o PENDING_REVIEW (recibido %)', NEW.status
                USING ERRCODE = 'check_violation';
        END IF;
        RETURN NEW;
    END IF;

    IF NEW.status IS DISTINCT FROM OLD.status
       AND (OLD.status, NEW.status) NOT IN (
            ('DRAFT', 'PENDING_REVIEW'), ('DRAFT', 'RETIRED'),
            ('PENDING_REVIEW', 'APPROVED'), ('PENDING_REVIEW', 'REJECTED'), ('PENDING_REVIEW', 'DRAFT'),
            ('REJECTED', 'DRAFT'), ('REJECTED', 'RETIRED'),
            ('APPROVED', 'SUSPENDED'), ('APPROVED', 'RETIRED'),
            ('SUSPENDED', 'APPROVED'), ('SUSPENDED', 'RETIRED')) THEN
        RAISE EXCEPTION 'Transición de experiencia no permitida: % -> %', OLD.status, NEW.status
            USING ERRCODE = 'check_violation';
    END IF;

    IF NEW.max_capacity_per_slot < OLD.max_capacity_per_slot AND EXISTS (
        SELECT 1
          FROM catalog.schedules s
         WHERE s.experience_id = NEW.id
           AND s.status = 'OPEN'
           AND s.start_time > now()
           AND s.total_slots > NEW.max_capacity_per_slot) THEN
        RAISE EXCEPTION 'Existen franjas futuras abiertas con aforo mayor a %', NEW.max_capacity_per_slot
            USING ERRCODE = 'check_violation';
    END IF;

    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION catalog.fn_experience_status_log()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        INSERT INTO catalog.experience_status_history (experience_id, from_status, to_status, reason, changed_by)
        VALUES (NEW.id, NULL, NEW.status, common.current_reason(), common.current_actor());
    ELSIF NEW.status IS DISTINCT FROM OLD.status THEN
        INSERT INTO catalog.experience_status_history (experience_id, from_status, to_status, reason, changed_by)
        VALUES (NEW.id, OLD.status, NEW.status,
                COALESCE(common.current_reason(), CASE WHEN NEW.status = 'REJECTED' THEN NEW.rejection_reason END),
                common.current_actor());
    END IF;
    RETURN NULL;
END;
$$;

-- -----------------------------------------------------------------------------
-- catalog.schedules (franja horaria: objetivo del SELECT ... FOR UPDATE)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS catalog.schedules (
    id                   UUID          NOT NULL DEFAULT gen_random_uuid(),
    experience_id        UUID          NOT NULL,
    start_time           TIMESTAMPTZ   NOT NULL,
    end_time             TIMESTAMPTZ   NOT NULL,
    total_slots          SMALLINT      NOT NULL,
    available_slots      SMALLINT      NOT NULL,
    status               VARCHAR(20)   NOT NULL DEFAULT 'OPEN',
    cancellation_reason  TEXT,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by           UUID          DEFAULT common.current_actor(),
    CONSTRAINT pk_schedules PRIMARY KEY (id),
    CONSTRAINT uq_schedules_experience_start UNIQUE (experience_id, start_time),
    CONSTRAINT ck_schedules_period CHECK (end_time > start_time),
    CONSTRAINT ck_schedules_total_slots CHECK (total_slots BETWEEN 1 AND 30),
    CONSTRAINT ck_schedules_available_slots CHECK (available_slots >= 0 AND available_slots <= total_slots),
    CONSTRAINT ck_schedules_status CHECK (status IN ('OPEN', 'CLOSED', 'CANCELLED')),
    CONSTRAINT ck_schedules_cancellation CHECK (status <> 'CANCELLED' OR cancellation_reason IS NOT NULL),
    CONSTRAINT fk_schedules_experience FOREIGN KEY (experience_id)
        REFERENCES catalog.experiences (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_schedules_created_by FOREIGN KEY (created_by)
        REFERENCES auth.users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX IF NOT EXISTS ix_schedules_open_start
    ON catalog.schedules (start_time) WHERE status = 'OPEN';
SELECT common.attach_updated_at('catalog.schedules');

COMMENT ON TABLE  catalog.schedules IS 'Franja horaria concreta de una experiencia, con su aforo. Es la fila que bloquea el motor de reservas con SELECT ... FOR UPDATE (RF-09).';
COMMENT ON COLUMN catalog.schedules.total_slots IS 'Aforo propio de la franja; debe ser <= experiences.max_capacity_per_slot (D5, validado por trigger).';
COMMENT ON COLUMN catalog.schedules.available_slots IS 'Desnormalización controlada (D8): contador transaccional de cupos libres. Se concilia con booking.v_schedule_slot_reconciliation.';
COMMENT ON COLUMN catalog.schedules.status IS 'OPEN (admite reservas) | CLOSED (sin nuevas reservas, p. ej. experiencia suspendida) | CANCELLED (cancelada por clima u otra causa).';

CREATE OR REPLACE FUNCTION catalog.fn_schedule_capacity_guard()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_max SMALLINT;
BEGIN
    SELECT e.max_capacity_per_slot INTO v_max
      FROM catalog.experiences e
     WHERE e.id = NEW.experience_id;

    IF NEW.total_slots > v_max THEN
        RAISE EXCEPTION 'El aforo de la franja (%) supera la capacidad de carga de la experiencia (%)',
            NEW.total_slots, v_max
            USING ERRCODE = 'check_violation';
    END IF;
    IF TG_OP = 'INSERT' AND NEW.available_slots <> NEW.total_slots THEN
        RAISE EXCEPTION 'Una franja nueva debe iniciar con todos sus cupos disponibles'
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE TRIGGER trg_schedule_capacity_guard
    BEFORE INSERT OR UPDATE OF total_slots, experience_id ON catalog.schedules
    FOR EACH ROW EXECUTE FUNCTION catalog.fn_schedule_capacity_guard();

CREATE OR REPLACE TRIGGER trg_experience_guard
    BEFORE INSERT OR UPDATE ON catalog.experiences
    FOR EACH ROW EXECUTE FUNCTION catalog.fn_experience_guard();

CREATE OR REPLACE TRIGGER trg_experience_status_log
    AFTER INSERT OR UPDATE OF status ON catalog.experiences
    FOR EACH ROW EXECUTE FUNCTION catalog.fn_experience_status_log();

-- -----------------------------------------------------------------------------
-- Supresión de datos sensibles del anfitrión (complementa auth.anonymize_user).
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION catalog.anonymize_host_sensitive_data(p_user_id UUID)
RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
    UPDATE catalog.host_profiles
       SET victim_condition_enc        = NULL,
           ruv_registration_number_enc = NULL,
           victimization_type_enc      = NULL,
           support_doc_s3_key          = NULL,
           sensitive_key_version       = NULL,
           deleted_at                  = COALESCE(deleted_at, now())
     WHERE user_id = p_user_id;
END;
$$;

COMMENT ON FUNCTION catalog.anonymize_host_sensitive_data(UUID) IS
    'Elimina los datos sensibles de víctima del anfitrión. El objeto S3 del soporte se borra en la aplicación.';
