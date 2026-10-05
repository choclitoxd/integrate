-- =============================================================================
-- V4 · Módulo booking: reservas, participantes, historial de estados y
--      cancelaciones.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- booking.bookings
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS booking.bookings (
    id                           UUID               NOT NULL DEFAULT gen_random_uuid(),
    tourist_id                   UUID               NOT NULL,
    schedule_id                  UUID               NOT NULL,
    slots_count                  SMALLINT           NOT NULL,
    unit_price_cop               common.cop_amount  NOT NULL,
    total_amount_cop             common.cop_amount  NOT NULL,
    currency                     CHAR(3)            NOT NULL DEFAULT 'COP',
    status                       VARCHAR(30)        NOT NULL DEFAULT 'PENDING',
    expires_at                   TIMESTAMPTZ        NOT NULL,
    confirmed_at                 TIMESTAMPTZ,
    emergency_contact_name_enc   common.ciphertext  NOT NULL,
    emergency_contact_phone_enc  common.ciphertext  NOT NULL,
    sensitive_key_version        SMALLINT           NOT NULL,
    created_at                   TIMESTAMPTZ        NOT NULL DEFAULT now(),
    updated_at                   TIMESTAMPTZ        NOT NULL DEFAULT now(),
    created_by                   UUID               DEFAULT common.current_actor(),
    CONSTRAINT pk_bookings PRIMARY KEY (id),
    CONSTRAINT ck_bookings_slots CHECK (slots_count BETWEEN 1 AND 30),
    CONSTRAINT ck_bookings_price CHECK (unit_price_cop > 0),
    CONSTRAINT ck_bookings_total CHECK (total_amount_cop = unit_price_cop * slots_count),
    CONSTRAINT ck_bookings_currency CHECK (currency = 'COP'),
    CONSTRAINT ck_bookings_status CHECK (status IN (
        'PENDING', 'CONFIRMED', 'PAYMENT_FAILED', 'EXPIRED',
        'CANCELLED_REFUNDED', 'CANCELLED_NO_REFUND', 'CANCELLED_BY_PLATFORM',
        'EXECUTED', 'NO_SHOW', 'SYNC_CONFLICT')),
    CONSTRAINT ck_bookings_ttl CHECK (expires_at > created_at),
    CONSTRAINT ck_bookings_confirmed_at CHECK (status <> 'CONFIRMED' OR confirmed_at IS NOT NULL),
    CONSTRAINT ck_bookings_key_version CHECK (sensitive_key_version > 0),
    CONSTRAINT fk_bookings_tourist FOREIGN KEY (tourist_id)
        REFERENCES auth.users (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_bookings_schedule FOREIGN KEY (schedule_id)
        REFERENCES catalog.schedules (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_bookings_created_by FOREIGN KEY (created_by)
        REFERENCES auth.users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

-- Worker de expiración (RF-11): barre solo las PENDING vencidas.
CREATE INDEX IF NOT EXISTS ix_bookings_pending_expiry
    ON booking.bookings (expires_at) WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS ix_bookings_tourist ON booking.bookings (tourist_id, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_bookings_schedule_status ON booking.bookings (schedule_id, status);
SELECT common.attach_updated_at('booking.bookings');

COMMENT ON TABLE  booking.bookings IS 'Reserva de N cupos de una franja por un turista. Nace PENDING con TTL de 15 minutos dentro de la transacción de bloqueo pesimista (RF-09).';
COMMENT ON COLUMN booking.bookings.slots_count IS 'Cupos descontados de catalog.schedules.available_slots. No se permiten cancelaciones parciales (E5).';
COMMENT ON COLUMN booking.bookings.unit_price_cop IS 'Copia del precio por persona vigente al reservar (D3). El trigger exige que coincida con la experiencia.';
COMMENT ON COLUMN booking.bookings.total_amount_cop IS 'slots_count × unit_price_cop (CHECK).';
COMMENT ON COLUMN booking.bookings.status IS 'Máquina de estados E3; las transiciones válidas las impone booking.fn_booking_update_guard.';
COMMENT ON COLUMN booking.bookings.expires_at IS 'Fecha límite de pago: created_at + 15 min (RF-09/RF-11).';
COMMENT ON COLUMN booking.bookings.emergency_contact_name_enc IS 'Nombre del contacto de emergencia, cifrado AES-256-GCM (B4). Uno por reserva (E1).';
COMMENT ON COLUMN booking.bookings.emergency_contact_phone_enc IS 'Teléfono del contacto de emergencia, cifrado AES-256-GCM (B4).';

-- -----------------------------------------------------------------------------
-- booking.booking_participants (una fila por cupo; E1)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS booking.booking_participants (
    id                     BIGINT              GENERATED ALWAYS AS IDENTITY,
    booking_id             UUID                NOT NULL,
    full_name              VARCHAR(150)        NOT NULL,
    doc_type               common.doc_type     NOT NULL,
    doc_number             common.doc_number   NOT NULL,
    eps_enc                common.ciphertext   NOT NULL,
    sensitive_key_version  SMALLINT            NOT NULL,
    is_holder              BOOLEAN             NOT NULL DEFAULT FALSE,
    created_at             TIMESTAMPTZ         NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ         NOT NULL DEFAULT now(),
    created_by             UUID                DEFAULT common.current_actor(),
    CONSTRAINT pk_booking_participants PRIMARY KEY (id),
    CONSTRAINT uq_booking_participants_document UNIQUE (booking_id, doc_type, doc_number),
    CONSTRAINT ck_booking_participants_name CHECK (length(btrim(full_name)) >= 3),
    CONSTRAINT ck_booking_participants_key_version CHECK (sensitive_key_version > 0),
    CONSTRAINT fk_booking_participants_booking FOREIGN KEY (booking_id)
        REFERENCES booking.bookings (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_booking_participants_created_by FOREIGN KEY (created_by)
        REFERENCES auth.users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_booking_participants_holder
    ON booking.booking_participants (booking_id) WHERE is_holder;
SELECT common.attach_updated_at('booking.booking_participants');

COMMENT ON TABLE  booking.booking_participants IS 'Persona que ocupa cada cupo. Para iniciar el pago debe haber exactamente slots_count participantes y un titular (RF-10).';
COMMENT ON COLUMN booking.booking_participants.eps_enc IS 'EPS o seguro médico del participante, cifrado AES-256-GCM (B4, dato de salud).';
COMMENT ON COLUMN booking.booking_participants.is_holder IS 'Titular cuyo documento se imprime en el voucher (claim doc) y se verifica en campo (R-03).';

-- -----------------------------------------------------------------------------
-- booking.booking_status_history (append-only)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS booking.booking_status_history (
    id           BIGINT        GENERATED ALWAYS AS IDENTITY,
    booking_id   UUID          NOT NULL,
    from_status  VARCHAR(30),
    to_status    VARCHAR(30)   NOT NULL,
    reason       TEXT,
    changed_by   UUID,
    changed_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_booking_status_history PRIMARY KEY (id),
    CONSTRAINT fk_booking_status_history_booking FOREIGN KEY (booking_id)
        REFERENCES booking.bookings (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_booking_status_history_changed_by FOREIGN KEY (changed_by)
        REFERENCES auth.users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);
CREATE INDEX IF NOT EXISTS ix_booking_status_history_booking
    ON booking.booking_status_history (booking_id, changed_at DESC);
SELECT common.make_append_only('booking.booking_status_history');

COMMENT ON TABLE booking.booking_status_history IS 'Bitácora inmutable del ciclo de vida de cada reserva (alimentada por trigger). changed_by NULL = proceso del sistema (worker TTL, webhook).';

-- -----------------------------------------------------------------------------
-- booking.booking_cancellations (0..1 por reserva; append-only; E5)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS booking.booking_cancellations (
    id                  BIGINT              GENERATED ALWAYS AS IDENTITY,
    booking_id          UUID                NOT NULL,
    actor_type          VARCHAR(10)         NOT NULL,
    cancelled_by        UUID,
    reason              TEXT                NOT NULL,
    hours_before_start  NUMERIC(10, 2)      NOT NULL,
    policy_applied      VARCHAR(30)         NOT NULL,
    tourist_refund_pct  common.percentage   NOT NULL,
    cancelled_at        TIMESTAMPTZ         NOT NULL DEFAULT now(),
    CONSTRAINT pk_booking_cancellations PRIMARY KEY (id),
    CONSTRAINT uq_booking_cancellations_booking UNIQUE (booking_id),
    CONSTRAINT ck_booking_cancellations_actor CHECK (actor_type IN ('TOURIST', 'ADMIN', 'SYSTEM')),
    CONSTRAINT ck_booking_cancellations_actor_id CHECK ((actor_type = 'SYSTEM') = (cancelled_by IS NULL)),
    CONSTRAINT ck_booking_cancellations_policy CHECK (policy_applied IN
        ('EARLY_REFUND', 'LATE_NO_REFUND', 'PLATFORM_FULL_REFUND')),
    CONSTRAINT ck_booking_cancellations_pct CHECK (
        (policy_applied = 'PLATFORM_FULL_REFUND' AND tourist_refund_pct = 100) OR
        (policy_applied = 'LATE_NO_REFUND' AND tourist_refund_pct = 0) OR
        (policy_applied = 'EARLY_REFUND' AND tourist_refund_pct > 0 AND tourist_refund_pct < 100)
    ),
    CONSTRAINT fk_booking_cancellations_booking FOREIGN KEY (booking_id)
        REFERENCES booking.bookings (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_booking_cancellations_cancelled_by FOREIGN KEY (cancelled_by)
        REFERENCES auth.users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);
SELECT common.make_append_only('booking.booking_cancellations');

COMMENT ON TABLE  booking.booking_cancellations IS 'Hecho jurídico de la cancelación total de una reserva y la política aplicada (RF-13, RF-14, RF-08/E4). Inmutable.';
COMMENT ON COLUMN booking.booking_cancellations.hours_before_start IS 'Δt = inicio de la franja − cancelación, en horas UTC. Se guarda como evidencia de la regla de 72 h aplicada.';
COMMENT ON COLUMN booking.booking_cancellations.policy_applied IS 'EARLY_REFUND (> umbral: 85% al turista) | LATE_NO_REFUND (<= umbral: 0%) | PLATFORM_FULL_REFUND (suspensión: 100%).';

-- -----------------------------------------------------------------------------
-- Triggers de integridad de la reserva
-- -----------------------------------------------------------------------------

-- Inserción: franja abierta y futura, experiencia aprobada, precio vigente y
-- turista activo con rol TOURIST. Se ejecuta dentro de la transacción de bloqueo.
CREATE OR REPLACE FUNCTION booking.fn_booking_insert_guard()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_schedule_status  VARCHAR(20);
    v_start_time       TIMESTAMPTZ;
    v_exp_status       VARCHAR(20);
    v_exp_deleted_at   TIMESTAMPTZ;
    v_price            NUMERIC(14, 2);
BEGIN
    IF NEW.status <> 'PENDING' THEN
        RAISE EXCEPTION 'Una reserva nace en estado PENDING' USING ERRCODE = 'check_violation';
    END IF;

    SELECT s.status, s.start_time, e.status, e.deleted_at, e.unit_price_cop
      INTO v_schedule_status, v_start_time, v_exp_status, v_exp_deleted_at, v_price
      FROM catalog.schedules s
      JOIN catalog.experiences e ON e.id = s.experience_id
     WHERE s.id = NEW.schedule_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'La franja % no existe', NEW.schedule_id USING ERRCODE = 'foreign_key_violation';
    END IF;
    IF v_schedule_status <> 'OPEN' OR v_exp_status <> 'APPROVED' OR v_exp_deleted_at IS NOT NULL THEN
        RAISE EXCEPTION 'La franja % no admite reservas', NEW.schedule_id USING ERRCODE = 'check_violation';
    END IF;
    IF v_start_time <= now() THEN
        RAISE EXCEPTION 'La franja % ya inició', NEW.schedule_id USING ERRCODE = 'check_violation';
    END IF;
    IF NEW.unit_price_cop <> v_price THEN
        RAISE EXCEPTION 'El precio unitario no coincide con la tarifa vigente (%)', v_price
            USING ERRCODE = 'check_violation';
    END IF;
    IF NOT EXISTS (
        SELECT 1
          FROM auth.user_roles ur
          JOIN auth.roles r ON r.id = ur.role_id
          JOIN auth.users u ON u.id = ur.user_id
         WHERE ur.user_id = NEW.tourist_id
           AND ur.revoked_at IS NULL
           AND r.code = 'TOURIST'
           AND u.status = 'ACTIVE'
           AND u.deleted_at IS NULL) THEN
        RAISE EXCEPTION 'El usuario % no es un turista activo', NEW.tourist_id
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;

-- Actualización: datos económicos inmutables y transiciones de estado válidas (E3).
CREATE OR REPLACE FUNCTION booking.fn_booking_update_guard()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF ROW(NEW.tourist_id, NEW.schedule_id, NEW.slots_count, NEW.unit_price_cop,
           NEW.total_amount_cop, NEW.currency, NEW.expires_at)
       IS DISTINCT FROM
       ROW(OLD.tourist_id, OLD.schedule_id, OLD.slots_count, OLD.unit_price_cop,
           OLD.total_amount_cop, OLD.currency, OLD.expires_at) THEN
        RAISE EXCEPTION 'Los datos económicos de la reserva % son inmutables', OLD.id
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.status IS DISTINCT FROM OLD.status THEN
        IF (OLD.status, NEW.status) NOT IN (
            ('PENDING', 'CONFIRMED'), ('PENDING', 'PAYMENT_FAILED'), ('PENDING', 'EXPIRED'),
            ('PENDING', 'CANCELLED_BY_PLATFORM'),
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

CREATE OR REPLACE FUNCTION booking.fn_booking_status_log()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        INSERT INTO booking.booking_status_history (booking_id, from_status, to_status, reason, changed_by)
        VALUES (NEW.id, NULL, NEW.status, common.current_reason(), common.current_actor());
    ELSIF NEW.status IS DISTINCT FROM OLD.status THEN
        INSERT INTO booking.booking_status_history (booking_id, from_status, to_status, reason, changed_by)
        VALUES (NEW.id, OLD.status, NEW.status, common.current_reason(), common.current_actor());
    END IF;
    RETURN NULL;
END;
$$;

CREATE OR REPLACE TRIGGER trg_booking_insert_guard
    BEFORE INSERT ON booking.bookings
    FOR EACH ROW EXECUTE FUNCTION booking.fn_booking_insert_guard();

CREATE OR REPLACE TRIGGER trg_booking_update_guard
    BEFORE UPDATE ON booking.bookings
    FOR EACH ROW EXECUTE FUNCTION booking.fn_booking_update_guard();

CREATE OR REPLACE TRIGGER trg_booking_status_log
    AFTER INSERT OR UPDATE OF status ON booking.bookings
    FOR EACH ROW EXECUTE FUNCTION booking.fn_booking_status_log();

-- Participantes: solo editables mientras la reserva está PENDING y sin exceder los cupos.
CREATE OR REPLACE FUNCTION booking.fn_participant_guard()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_status  VARCHAR(30);
    v_slots   SMALLINT;
    v_count   INTEGER;
BEGIN
    SELECT b.status, b.slots_count INTO v_status, v_slots
      FROM booking.bookings b
     WHERE b.id = NEW.booking_id
       FOR UPDATE;

    IF v_status <> 'PENDING' THEN
        RAISE EXCEPTION 'Los participantes solo se registran o corrigen con la reserva en PENDING (estado %)', v_status
            USING ERRCODE = 'check_violation';
    END IF;

    IF TG_OP = 'INSERT' THEN
        SELECT count(*) INTO v_count
          FROM booking.booking_participants p
         WHERE p.booking_id = NEW.booking_id;
        IF v_count >= v_slots THEN
            RAISE EXCEPTION 'La reserva % ya tiene sus % participantes', NEW.booking_id, v_slots
                USING ERRCODE = 'check_violation';
        END IF;
    ELSIF NEW.booking_id <> OLD.booking_id THEN
        RAISE EXCEPTION 'Un participante no puede moverse a otra reserva' USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE TRIGGER trg_participant_guard
    BEFORE INSERT OR UPDATE ON booking.booking_participants
    FOR EACH ROW EXECUTE FUNCTION booking.fn_participant_guard();

-- -----------------------------------------------------------------------------
-- Conciliación del contador desnormalizado available_slots (D8)
-- -----------------------------------------------------------------------------
CREATE OR REPLACE VIEW booking.v_schedule_slot_reconciliation AS
SELECT s.id                                         AS schedule_id,
       s.total_slots,
       s.available_slots,
       COALESCE(sum(b.slots_count), 0)::INTEGER     AS consumed_slots,
       s.total_slots - COALESCE(sum(b.slots_count), 0)::INTEGER AS expected_available_slots,
       (s.available_slots <> s.total_slots - COALESCE(sum(b.slots_count), 0)) AS has_drift
  FROM catalog.schedules s
  LEFT JOIN booking.bookings b
         ON b.schedule_id = s.id
        AND b.status IN ('PENDING', 'CONFIRMED', 'CANCELLED_NO_REFUND')
 WHERE s.start_time > now()
 GROUP BY s.id, s.total_slots, s.available_slots;

COMMENT ON VIEW booking.v_schedule_slot_reconciliation IS
    'Para franjas futuras, compara available_slots con el valor derivado de las reservas que consumen cupo (PENDING, CONFIRMED, CANCELLED_NO_REFUND). has_drift = TRUE indica una inconsistencia a investigar. Liberan cupo: PAYMENT_FAILED, EXPIRED, CANCELLED_REFUNDED, CANCELLED_BY_PLATFORM. Los estados posteriores al evento no aplican.';
