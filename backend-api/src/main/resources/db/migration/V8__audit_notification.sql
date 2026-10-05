-- =============================================================================
-- V8 · Bitácora inmutable encadenada (audit) y transactional outbox
--      (notification).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- audit.chain_head: cabeza única de la cadena de hashes.
-- Serializa las inserciones en la bitácora mediante el bloqueo de esta fila.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit.chain_head (
    id          SMALLINT           NOT NULL DEFAULT 1,
    last_id     BIGINT             NOT NULL DEFAULT 0,
    last_hash   common.sha256_hex  NOT NULL DEFAULT repeat('0', 64),
    updated_at  TIMESTAMPTZ        NOT NULL DEFAULT now(),
    CONSTRAINT pk_chain_head PRIMARY KEY (id),
    CONSTRAINT ck_chain_head_singleton CHECK (id = 1)
);
INSERT INTO audit.chain_head (id) VALUES (1) ON CONFLICT (id) DO NOTHING;

COMMENT ON TABLE audit.chain_head IS 'Fila única con el último id y hash de audit.audit_trail. Solo la modifica el trigger SECURITY DEFINER; la aplicación no tiene privilegios sobre ella.';

-- -----------------------------------------------------------------------------
-- audit.audit_trail (particionada por año; A6, I1, I2, I3)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit.audit_trail (
    id              BIGINT             NOT NULL,
    created_at      TIMESTAMPTZ        NOT NULL DEFAULT now(),
    module          VARCHAR(20)        NOT NULL,
    entity_name     VARCHAR(60)        NOT NULL,
    entity_id       VARCHAR(100)       NOT NULL,
    action          VARCHAR(60)        NOT NULL,
    severity        VARCHAR(10)        NOT NULL DEFAULT 'INFO',
    actor_id        UUID,
    actor_ip        INET,
    user_agent      VARCHAR(1000),
    correlation_id  UUID,
    before_data     JSONB,
    after_data      JSONB,
    details         JSONB              NOT NULL DEFAULT '{}'::jsonb,
    prev_hash       common.sha256_hex  NOT NULL,
    row_hash        common.sha256_hex  NOT NULL,
    CONSTRAINT pk_audit_trail PRIMARY KEY (id, created_at),
    CONSTRAINT ck_audit_trail_module CHECK (module IN
        ('auth', 'catalog', 'booking', 'payments', 'voucher', 'offline', 'notification', 'admin')),
    CONSTRAINT ck_audit_trail_severity CHECK (severity IN ('INFO', 'WARNING', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_audit_trail_details CHECK (jsonb_typeof(details) = 'object'),
    CONSTRAINT fk_audit_trail_actor FOREIGN KEY (actor_id)
        REFERENCES auth.users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) PARTITION BY RANGE (created_at);

CREATE INDEX IF NOT EXISTS ix_audit_trail_entity ON audit.audit_trail (entity_name, entity_id, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_audit_trail_actor ON audit.audit_trail (actor_id, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_audit_trail_action ON audit.audit_trail (module, action, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_audit_trail_high_severity
    ON audit.audit_trail (created_at DESC) WHERE severity IN ('HIGH', 'CRITICAL');
CREATE INDEX IF NOT EXISTS ix_audit_trail_details ON audit.audit_trail USING gin (details jsonb_path_ops);

COMMENT ON TABLE  audit.audit_trail IS 'Bitácora append-only de operaciones críticas (RF-20): consentimientos, bloqueos de cupo, pagos, cancelaciones, validaciones offline e incidentes. Encadenada con SHA-256 para no repudio. Retención mínima de 10 años (RNF-09).';
COMMENT ON COLUMN audit.audit_trail.id IS 'Secuencia sin huecos asignada por el trigger de encadenamiento: un hueco evidencia una fila eliminada.';
COMMENT ON COLUMN audit.audit_trail.entity_id IS 'Identificador de la entidad afectada como texto (admite UUID o BIGINT).';
COMMENT ON COLUMN audit.audit_trail.before_data IS 'Estado previo relevante. No debe contener datos sensibles en claro (solo referencias).';
COMMENT ON COLUMN audit.audit_trail.prev_hash IS 'row_hash de la fila anterior (id - 1); 64 ceros para la primera.';
COMMENT ON COLUMN audit.audit_trail.row_hash IS 'SHA-256 de la representación canónica de la fila, incluido prev_hash (ver audit.fn_row_hash).';

-- Particiones anuales: 2026–2031 creadas aquí; las siguientes con audit.ensure_year_partition().
CREATE OR REPLACE FUNCTION audit.ensure_year_partition(p_year INTEGER)
RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
    EXECUTE format(
        'CREATE TABLE IF NOT EXISTS audit.%I PARTITION OF audit.audit_trail FOR VALUES FROM (%L) TO (%L)',
        'audit_trail_y' || p_year,
        make_timestamptz(p_year, 1, 1, 0, 0, 0, 'UTC'),
        make_timestamptz(p_year + 1, 1, 1, 0, 0, 0, 'UTC'));
END;
$$;

COMMENT ON FUNCTION audit.ensure_year_partition(INTEGER) IS
    'Crea (si no existe) la partición anual de audit.audit_trail. Debe ejecutarla integrate_owner antes de cada año nuevo (job programado o migración).';

SELECT audit.ensure_year_partition(y) FROM generate_series(2026, 2031) AS y;

CREATE TABLE IF NOT EXISTS audit.audit_trail_default
    PARTITION OF audit.audit_trail DEFAULT;
COMMENT ON TABLE audit.audit_trail_default IS 'Red de seguridad si falta la partición anual. Debe permanecer vacía; vigilar con monitoreo.';

-- Hash canónico: jsonb_build_array produce un texto determinista e inequívoco.
-- Recibe campos sueltos (no el tipo fila) porque el trigger se ejecuta sobre la
-- partición, cuyo tipo compuesto es distinto del de la tabla padre.
CREATE OR REPLACE FUNCTION audit.fn_row_hash(
    p_prev_hash TEXT, p_id BIGINT, p_created_at TIMESTAMPTZ,
    p_module TEXT, p_entity_name TEXT, p_entity_id TEXT, p_action TEXT, p_severity TEXT,
    p_actor_id UUID, p_actor_ip INET, p_user_agent TEXT, p_correlation_id UUID,
    p_before_data JSONB, p_after_data JSONB, p_details JSONB)
RETURNS TEXT
LANGUAGE sql
STABLE
AS $$
    SELECT encode(sha256(convert_to(jsonb_build_array(
        p_prev_hash,
        p_id,
        to_char(p_created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
        p_module, p_entity_name, p_entity_id, p_action, p_severity,
        p_actor_id::text, p_actor_ip::text, p_user_agent, p_correlation_id::text,
        p_before_data, p_after_data, p_details
    )::text, 'UTF8')), 'hex')
$$;

COMMENT ON FUNCTION audit.fn_row_hash(TEXT, BIGINT, TIMESTAMPTZ, TEXT, TEXT, TEXT, TEXT, TEXT, UUID, INET, TEXT, UUID, JSONB, JSONB, JSONB) IS
    'Calcula el SHA-256 canónico de una fila de la bitácora (independiente de la zona horaria de la sesión).';

CREATE OR REPLACE FUNCTION audit.fn_chain_append()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    v_last_id    BIGINT;
    v_last_hash  TEXT;
BEGIN
    -- FOR UPDATE en READ COMMITTED espera y relee la versión confirmada más reciente:
    -- la cadena nunca se bifurca aunque haya inserciones concurrentes.
    SELECT ch.last_id, ch.last_hash INTO v_last_id, v_last_hash
      FROM audit.chain_head ch
     WHERE ch.id = 1
       FOR UPDATE;

    NEW.id        := v_last_id + 1;
    NEW.prev_hash := v_last_hash;
    NEW.row_hash  := audit.fn_row_hash(
        NEW.prev_hash, NEW.id, NEW.created_at,
        NEW.module, NEW.entity_name, NEW.entity_id, NEW.action, NEW.severity,
        NEW.actor_id, NEW.actor_ip, NEW.user_agent, NEW.correlation_id,
        NEW.before_data, NEW.after_data, NEW.details);

    UPDATE audit.chain_head
       SET last_id = NEW.id, last_hash = NEW.row_hash, updated_at = now()
     WHERE id = 1;
    RETURN NEW;
END;
$$;

COMMENT ON FUNCTION audit.fn_chain_append() IS
    'Asigna id consecutivo, prev_hash y row_hash. Mantiene bloqueada la cabeza hasta el COMMIT: insertar la auditoría como última sentencia de la transacción para minimizar la espera.';

CREATE OR REPLACE TRIGGER trg_audit_chain_append
    BEFORE INSERT ON audit.audit_trail
    FOR EACH ROW EXECUTE FUNCTION audit.fn_chain_append();

SELECT common.make_append_only('audit.audit_trail');

-- Verificación de integridad de la cadena (uso del administrador o de un job).
CREATE OR REPLACE FUNCTION audit.verify_chain(p_from_id BIGINT DEFAULT 1, p_to_id BIGINT DEFAULT NULL)
RETURNS TABLE (broken_id BIGINT, problem TEXT)
LANGUAGE plpgsql
STABLE
AS $$
DECLARE
    r            audit.audit_trail;
    v_prev       TEXT;
    v_expected   BIGINT := p_from_id;
BEGIN
    IF p_from_id = 1 THEN
        v_prev := repeat('0', 64);
    ELSE
        SELECT a.row_hash INTO v_prev FROM audit.audit_trail a WHERE a.id = p_from_id - 1;
        IF v_prev IS NULL THEN
            broken_id := p_from_id - 1;
            problem   := 'No existe la fila anterior al rango solicitado';
            RETURN NEXT;
            RETURN;
        END IF;
    END IF;

    FOR r IN
        SELECT * FROM audit.audit_trail a
         WHERE a.id >= p_from_id AND (p_to_id IS NULL OR a.id <= p_to_id)
         ORDER BY a.id
    LOOP
        IF r.id <> v_expected THEN
            broken_id := v_expected; problem := 'Hueco en la secuencia: fila eliminada';
            RETURN NEXT; RETURN;
        END IF;
        IF r.prev_hash <> v_prev THEN
            broken_id := r.id; problem := 'prev_hash no coincide con la fila anterior';
            RETURN NEXT; RETURN;
        END IF;
        IF r.row_hash <> audit.fn_row_hash(
                r.prev_hash, r.id, r.created_at,
                r.module, r.entity_name, r.entity_id, r.action, r.severity,
                r.actor_id, r.actor_ip, r.user_agent, r.correlation_id,
                r.before_data, r.after_data, r.details) THEN
            broken_id := r.id; problem := 'row_hash no coincide: contenido alterado';
            RETURN NEXT; RETURN;
        END IF;
        v_prev     := r.row_hash;
        v_expected := v_expected + 1;
    END LOOP;
END;
$$;

COMMENT ON FUNCTION audit.verify_chain(BIGINT, BIGINT) IS
    'Recorre la bitácora en orden y devuelve la primera fila rota (hueco, encadenamiento o contenido). Sin filas = cadena íntegra.';

-- -----------------------------------------------------------------------------
-- notification.notification_outbox (I4: Transactional Outbox)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notification.notification_outbox (
    id                   UUID           NOT NULL DEFAULT gen_random_uuid(),
    aggregate_type       VARCHAR(40)    NOT NULL,
    aggregate_id         VARCHAR(100)   NOT NULL,
    event_type           VARCHAR(60)    NOT NULL,
    channel              VARCHAR(10)    NOT NULL,
    recipient_user_id    UUID,
    recipient_address    VARCHAR(254)   NOT NULL,
    template_code        VARCHAR(60)    NOT NULL,
    payload              JSONB          NOT NULL DEFAULT '{}'::jsonb,
    attachments          JSONB          NOT NULL DEFAULT '[]'::jsonb,
    status               VARCHAR(15)    NOT NULL DEFAULT 'PENDING',
    attempts             SMALLINT       NOT NULL DEFAULT 0,
    max_attempts         SMALLINT       NOT NULL DEFAULT 8,
    next_attempt_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    last_error           TEXT,
    provider_message_id  VARCHAR(150),
    sent_at              TIMESTAMPTZ,
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT pk_notification_outbox PRIMARY KEY (id),
    CONSTRAINT ck_outbox_channel CHECK (channel IN ('EMAIL', 'WHATSAPP', 'SMS')),
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED', 'DEAD')),
    CONSTRAINT ck_outbox_attempts CHECK (attempts >= 0 AND max_attempts > 0 AND attempts <= max_attempts),
    CONSTRAINT ck_outbox_sent CHECK ((status = 'SENT') = (sent_at IS NOT NULL)),
    CONSTRAINT ck_outbox_payload CHECK (jsonb_typeof(payload) = 'object' AND jsonb_typeof(attachments) = 'array'),
    CONSTRAINT fk_outbox_recipient FOREIGN KEY (recipient_user_id)
        REFERENCES auth.users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

-- Despachador: SELECT ... WHERE status IN ('PENDING','FAILED') AND next_attempt_at <= now()
--              ORDER BY next_attempt_at FOR UPDATE SKIP LOCKED LIMIT n;
CREATE INDEX IF NOT EXISTS ix_outbox_dispatch
    ON notification.notification_outbox (next_attempt_at) WHERE status IN ('PENDING', 'FAILED');
CREATE INDEX IF NOT EXISTS ix_outbox_aggregate
    ON notification.notification_outbox (aggregate_type, aggregate_id);
CREATE INDEX IF NOT EXISTS ix_outbox_recipient ON notification.notification_outbox (recipient_user_id);
SELECT common.attach_updated_at('notification.notification_outbox');

COMMENT ON TABLE  notification.notification_outbox IS 'Transactional outbox: el evento se inserta en la misma transacción del cambio de negocio y un worker lo despacha (Gmail API/SES; WhatsApp/SMS con Twilio) con reintentos.';
COMMENT ON COLUMN notification.notification_outbox.recipient_address IS 'Correo o teléfono usado en el envío, como evidencia de notificación (puede diferir del dato actual del usuario).';
COMMENT ON COLUMN notification.notification_outbox.payload IS 'Variables de la plantilla. No incluir datos sensibles en claro (EPS, condición de víctima).';
COMMENT ON COLUMN notification.notification_outbox.attachments IS 'Arreglo de claves S3 (p. ej. PDF del voucher o del comprobante).';
COMMENT ON COLUMN notification.notification_outbox.status IS 'PENDING → PROCESSING → SENT; FAILED reintenta según next_attempt_at; DEAD al agotar max_attempts.';
