-- =============================================================================
-- V5 · Módulo payments: políticas de comisión, cuentas de dispersión, pagos,
--      webhooks, reembolsos, lotes de pago, dispersiones y comprobantes.
-- Regla rectora: el 85% de la comunidad es neto e intocable (F2, RF-18).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- payments.commission_policies (F6)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS payments.commission_policies (
    id                            SMALLINT            GENERATED ALWAYS AS IDENTITY,
    code                          VARCHAR(30)         NOT NULL,
    community_pct                 common.percentage   NOT NULL,
    platform_pct                  common.percentage   NOT NULL,
    early_cancel_refund_pct       common.percentage   NOT NULL,
    cancellation_threshold_hours  SMALLINT            NOT NULL,
    payout_grace_hours            SMALLINT            NOT NULL,
    valid_from                    TIMESTAMPTZ         NOT NULL,
    valid_to                      TIMESTAMPTZ,
    created_at                    TIMESTAMPTZ         NOT NULL DEFAULT now(),
    updated_at                    TIMESTAMPTZ         NOT NULL DEFAULT now(),
    created_by                    UUID                DEFAULT common.current_actor(),
    CONSTRAINT pk_commission_policies PRIMARY KEY (id),
    CONSTRAINT uq_commission_policies_code UNIQUE (code),
    CONSTRAINT ck_commission_policies_split CHECK (community_pct + platform_pct = 100 AND community_pct > 0),
    CONSTRAINT ck_commission_policies_threshold CHECK (cancellation_threshold_hours > 0),
    CONSTRAINT ck_commission_policies_grace CHECK (payout_grace_hours >= 0),
    CONSTRAINT ck_commission_policies_period CHECK (valid_to IS NULL OR valid_to > valid_from),
    CONSTRAINT ex_commission_policies_no_overlap EXCLUDE USING gist (
        tstzrange(valid_from, valid_to, '[)') WITH &&
    ),
    CONSTRAINT fk_commission_policies_created_by FOREIGN KEY (created_by)
        REFERENCES auth.users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);
SELECT common.attach_updated_at('payments.commission_policies');

-- Una política aplicada a pagos es evidencia contable: solo se puede cerrar su vigencia.
CREATE OR REPLACE FUNCTION payments.fn_commission_policy_freeze()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF ROW(NEW.code, NEW.community_pct, NEW.platform_pct, NEW.early_cancel_refund_pct,
           NEW.cancellation_threshold_hours, NEW.payout_grace_hours, NEW.valid_from)
       IS DISTINCT FROM
       ROW(OLD.code, OLD.community_pct, OLD.platform_pct, OLD.early_cancel_refund_pct,
           OLD.cancellation_threshold_hours, OLD.payout_grace_hours, OLD.valid_from)
       OR (OLD.valid_to IS NOT NULL AND NEW.valid_to IS DISTINCT FROM OLD.valid_to) THEN
        RAISE EXCEPTION 'La política % es inmutable; solo puede cerrarse su vigencia una vez', OLD.code
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE TRIGGER trg_commission_policy_freeze
    BEFORE UPDATE ON payments.commission_policies
    FOR EACH ROW EXECUTE FUNCTION payments.fn_commission_policy_freeze();

INSERT INTO payments.commission_policies
    (code, community_pct, platform_pct, early_cancel_refund_pct,
     cancellation_threshold_hours, payout_grace_hours, valid_from)
VALUES ('STANDARD_2026', 85.00, 15.00, 85.00, 72, 24, '2026-01-01T00:00:00Z')
ON CONFLICT (code) DO NOTHING;

COMMENT ON TABLE  payments.commission_policies IS 'Parámetros financieros versionados por vigencia: reparto 85/15, reembolso por cancelación anticipada, umbral de 72 h y gracia de 24 h para la dispersión.';
COMMENT ON COLUMN payments.commission_policies.community_pct IS 'Porcentaje neto para la asociación. Se redondea con CEIL a favor de la comunidad (F3).';
COMMENT ON COLUMN payments.commission_policies.platform_pct IS 'Comisión de la plataforma. De aquí sale la comisión de la pasarela (F2).';

-- -----------------------------------------------------------------------------
-- payments.payout_accounts (1:N con asociación; C5)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS payments.payout_accounts (
    id                      UUID               NOT NULL DEFAULT gen_random_uuid(),
    association_id          UUID               NOT NULL,
    account_type            VARCHAR(20)        NOT NULL,
    bank_code               VARCHAR(10),
    account_number_enc      common.ciphertext  NOT NULL,
    account_last4           CHAR(4)            NOT NULL,
    holder_name_enc         common.ciphertext  NOT NULL,
    holder_doc_type         VARCHAR(3)         NOT NULL,
    holder_doc_number_enc   common.ciphertext  NOT NULL,
    sensitive_key_version   SMALLINT           NOT NULL,
    is_default              BOOLEAN            NOT NULL DEFAULT FALSE,
    verified_at             TIMESTAMPTZ,
    created_at              TIMESTAMPTZ        NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ        NOT NULL DEFAULT now(),
    created_by              UUID               DEFAULT common.current_actor(),
    deleted_at              TIMESTAMPTZ,
    CONSTRAINT pk_payout_accounts PRIMARY KEY (id),
    CONSTRAINT uq_payout_accounts_id_association UNIQUE (id, association_id),
    CONSTRAINT ck_payout_accounts_type CHECK (account_type IN ('SAVINGS', 'CHECKING', 'NEQUI', 'DAVIPLATA')),
    CONSTRAINT ck_payout_accounts_bank CHECK (account_type IN ('NEQUI', 'DAVIPLATA') OR bank_code IS NOT NULL),
    CONSTRAINT ck_payout_accounts_last4 CHECK (account_last4 ~ '^[0-9]{4}$'),
    CONSTRAINT ck_payout_accounts_holder_doc CHECK (holder_doc_type IN ('NIT', 'CC', 'CE')),
    CONSTRAINT ck_payout_accounts_key_version CHECK (sensitive_key_version > 0),
    CONSTRAINT fk_payout_accounts_association FOREIGN KEY (association_id)
        REFERENCES catalog.community_associations (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_payout_accounts_created_by FOREIGN KEY (created_by)
        REFERENCES auth.users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_payout_accounts_default
    ON payments.payout_accounts (association_id) WHERE is_default AND deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_payout_accounts_association ON payments.payout_accounts (association_id);
SELECT common.attach_updated_at('payments.payout_accounts');

COMMENT ON TABLE  payments.payout_accounts IS 'Cuentas bancarias o billeteras (Nequi/Daviplata) de la asociación para recibir la dispersión. Datos financieros cifrados (B4).';
COMMENT ON COLUMN payments.payout_accounts.account_number_enc IS 'Número de cuenta o celular de la billetera, cifrado AES-256-GCM.';
COMMENT ON COLUMN payments.payout_accounts.account_last4 IS 'Últimos 4 dígitos en claro, solo para identificar la cuenta en la interfaz.';
COMMENT ON COLUMN payments.payout_accounts.bank_code IS 'Código ACH del banco; obligatorio salvo para billeteras.';

-- -----------------------------------------------------------------------------
-- payments.payment_transactions (1:N intentos por reserva; F1)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS payments.payment_transactions (
    id                      UUID               NOT NULL DEFAULT gen_random_uuid(),
    booking_id              UUID               NOT NULL,
    commission_policy_id    SMALLINT           NOT NULL,
    provider                VARCHAR(20)        NOT NULL,
    payment_method          VARCHAR(20),
    idempotency_key         VARCHAR(100)       NOT NULL,
    gateway_transaction_id  VARCHAR(100),
    gateway_reference       VARCHAR(100),
    amount_cop              common.cop_amount  NOT NULL,
    gateway_fee_cop         common.cop_amount,
    currency                CHAR(3)            NOT NULL DEFAULT 'COP',
    status                  VARCHAR(20)        NOT NULL DEFAULT 'INITIATED',
    status_detail           VARCHAR(255),
    approved_at             TIMESTAMPTZ,
    created_at              TIMESTAMPTZ        NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ        NOT NULL DEFAULT now(),
    created_by              UUID               DEFAULT common.current_actor(),
    CONSTRAINT pk_payment_transactions PRIMARY KEY (id),
    CONSTRAINT uq_payment_transactions_idempotency UNIQUE (idempotency_key),
    CONSTRAINT uq_payment_transactions_gateway UNIQUE (provider, gateway_transaction_id),
    CONSTRAINT ck_payment_transactions_provider CHECK (provider IN ('MOCK', 'WOMPI', 'EPAYCO')),
    CONSTRAINT ck_payment_transactions_method CHECK (payment_method IN ('PSE', 'CARD', 'NEQUI', 'DAVIPLATA')),
    CONSTRAINT ck_payment_transactions_amount CHECK (amount_cop > 0),
    CONSTRAINT ck_payment_transactions_currency CHECK (currency = 'COP'),
    CONSTRAINT ck_payment_transactions_status CHECK (status IN
        ('INITIATED', 'PENDING', 'APPROVED', 'REJECTED', 'ERROR', 'VOIDED')),
    CONSTRAINT ck_payment_transactions_approved CHECK ((status = 'APPROVED') = (approved_at IS NOT NULL)),
    CONSTRAINT fk_payment_transactions_booking FOREIGN KEY (booking_id)
        REFERENCES booking.bookings (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_payment_transactions_policy FOREIGN KEY (commission_policy_id)
        REFERENCES payments.commission_policies (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_payment_transactions_created_by FOREIGN KEY (created_by)
        REFERENCES auth.users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_transactions_one_approved
    ON payments.payment_transactions (booking_id) WHERE status = 'APPROVED';
CREATE INDEX IF NOT EXISTS ix_payment_transactions_booking ON payments.payment_transactions (booking_id);
CREATE INDEX IF NOT EXISTS ix_payment_transactions_policy ON payments.payment_transactions (commission_policy_id);
CREATE INDEX IF NOT EXISTS ix_payment_transactions_open
    ON payments.payment_transactions (created_at) WHERE status IN ('INITIATED', 'PENDING', 'ERROR');
SELECT common.attach_updated_at('payments.payment_transactions');

COMMENT ON TABLE  payments.payment_transactions IS 'Intento de cobro en COP a través del PaymentGatewayAdapter (RF-12). Una reserva admite varios intentos, pero solo uno APPROVED.';
COMMENT ON COLUMN payments.payment_transactions.idempotency_key IS 'Clave de idempotencia enviada a la pasarela para evitar dobles cobros (RNF-06).';
COMMENT ON COLUMN payments.payment_transactions.gateway_transaction_id IS 'ID de la transacción en la pasarela; único por proveedor (detección de webhooks duplicados).';
COMMENT ON COLUMN payments.payment_transactions.commission_policy_id IS 'Política vigente al iniciar el pago; congela los porcentajes aplicables.';
COMMENT ON COLUMN payments.payment_transactions.gateway_fee_cop IS 'Comisión cobrada por la pasarela; la absorbe la plataforma (F2).';

-- Inicio del pago: reserva PENDING, monto exacto, política vigente y participantes completos (RF-10).
CREATE OR REPLACE FUNCTION payments.fn_payment_insert_guard()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_status   VARCHAR(30);
    v_total    NUMERIC(14, 2);
    v_slots    SMALLINT;
    v_count    INTEGER;
    v_holders  INTEGER;
BEGIN
    SELECT b.status, b.total_amount_cop, b.slots_count
      INTO v_status, v_total, v_slots
      FROM booking.bookings b
     WHERE b.id = NEW.booking_id;

    IF v_status IS DISTINCT FROM 'PENDING' THEN
        RAISE EXCEPTION 'Solo una reserva PENDING puede iniciar un pago (estado %)', v_status
            USING ERRCODE = 'check_violation';
    END IF;
    IF NEW.amount_cop <> v_total THEN
        RAISE EXCEPTION 'El monto del pago (%) difiere del total de la reserva (%)', NEW.amount_cop, v_total
            USING ERRCODE = 'check_violation';
    END IF;

    SELECT count(*), count(*) FILTER (WHERE p.is_holder)
      INTO v_count, v_holders
      FROM booking.booking_participants p
     WHERE p.booking_id = NEW.booking_id;
    IF v_count <> v_slots OR v_holders <> 1 THEN
        RAISE EXCEPTION 'RF-10: la reserva requiere % participantes con EPS y un titular (hay %, titulares %)',
            v_slots, v_count, v_holders
            USING ERRCODE = 'check_violation';
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM payments.commission_policies cp
         WHERE cp.id = NEW.commission_policy_id
           AND cp.valid_from <= now()
           AND (cp.valid_to IS NULL OR cp.valid_to > now())) THEN
        RAISE EXCEPTION 'La política de comisión % no está vigente', NEW.commission_policy_id
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;

-- Un pago en estado final no cambia; los datos económicos son inmutables.
CREATE OR REPLACE FUNCTION payments.fn_payment_update_guard()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF ROW(NEW.booking_id, NEW.commission_policy_id, NEW.provider, NEW.idempotency_key, NEW.amount_cop, NEW.currency)
       IS DISTINCT FROM
       ROW(OLD.booking_id, OLD.commission_policy_id, OLD.provider, OLD.idempotency_key, OLD.amount_cop, OLD.currency) THEN
        RAISE EXCEPTION 'Los datos económicos del pago % son inmutables', OLD.id USING ERRCODE = 'restrict_violation';
    END IF;
    IF NEW.status IS DISTINCT FROM OLD.status THEN
        IF OLD.status IN ('APPROVED', 'REJECTED', 'VOIDED') THEN
            RAISE EXCEPTION 'El pago % está en estado final %', OLD.id, OLD.status USING ERRCODE = 'check_violation';
        END IF;
        IF NEW.status = 'APPROVED' AND NEW.approved_at IS NULL THEN
            NEW.approved_at := now();
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE TRIGGER trg_payment_insert_guard
    BEFORE INSERT ON payments.payment_transactions
    FOR EACH ROW EXECUTE FUNCTION payments.fn_payment_insert_guard();

CREATE OR REPLACE TRIGGER trg_payment_update_guard
    BEFORE UPDATE ON payments.payment_transactions
    FOR EACH ROW EXECUTE FUNCTION payments.fn_payment_update_guard();

-- -----------------------------------------------------------------------------
-- payments.payment_webhook_events (F7, RNF-06)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS payments.payment_webhook_events (
    id                BIGINT         GENERATED ALWAYS AS IDENTITY,
    provider          VARCHAR(20)    NOT NULL,
    idempotency_key   VARCHAR(150)   NOT NULL,
    payment_id        UUID,
    event_type        VARCHAR(60)    NOT NULL,
    gateway_status    VARCHAR(30),
    signature         VARCHAR(512)   NOT NULL,
    signature_valid   BOOLEAN        NOT NULL,
    payload           JSONB          NOT NULL,
    received_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    processed_at      TIMESTAMPTZ,
    processing_error  TEXT,
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT pk_payment_webhook_events PRIMARY KEY (id),
    CONSTRAINT uq_payment_webhook_events_idempotency UNIQUE (provider, idempotency_key),
    CONSTRAINT ck_payment_webhook_events_provider CHECK (provider IN ('MOCK', 'WOMPI', 'EPAYCO')),
    CONSTRAINT ck_payment_webhook_events_processing CHECK (signature_valid OR processed_at IS NULL OR processing_error IS NOT NULL),
    CONSTRAINT fk_payment_webhook_events_payment FOREIGN KEY (payment_id)
        REFERENCES payments.payment_transactions (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX IF NOT EXISTS ix_payment_webhook_events_payment ON payments.payment_webhook_events (payment_id);
CREATE INDEX IF NOT EXISTS ix_payment_webhook_events_unprocessed
    ON payments.payment_webhook_events (received_at) WHERE processed_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_payment_webhook_events_payload
    ON payments.payment_webhook_events USING gin (payload jsonb_path_ops);
SELECT common.attach_updated_at('payments.payment_webhook_events');

COMMENT ON TABLE  payments.payment_webhook_events IS 'Bandeja de entrada de webhooks de la pasarela. El UNIQUE (provider, idempotency_key) hace que un reintento duplicado responda 200 sin reprocesar (RF-12).';
COMMENT ON COLUMN payments.payment_webhook_events.signature IS 'Valor del header X-Signature (HMAC-SHA256) recibido.';
COMMENT ON COLUMN payments.payment_webhook_events.payload IS 'Cuerpo crudo del webhook, para auditoría y reprocesamiento.';

-- -----------------------------------------------------------------------------
-- payments.refunds (1:N con pago; F4)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS payments.refunds (
    id                       UUID               NOT NULL DEFAULT gen_random_uuid(),
    payment_id               UUID               NOT NULL,
    booking_cancellation_id  BIGINT,
    reason                   VARCHAR(30)        NOT NULL,
    amount_cop               common.cop_amount  NOT NULL,
    idempotency_key          VARCHAR(100)       NOT NULL,
    gateway_refund_id        VARCHAR(100),
    status                   VARCHAR(20)        NOT NULL DEFAULT 'REQUESTED',
    failure_reason           TEXT,
    completed_at             TIMESTAMPTZ,
    created_at               TIMESTAMPTZ        NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ        NOT NULL DEFAULT now(),
    created_by               UUID               DEFAULT common.current_actor(),
    CONSTRAINT pk_refunds PRIMARY KEY (id),
    CONSTRAINT uq_refunds_idempotency UNIQUE (idempotency_key),
    CONSTRAINT uq_refunds_gateway UNIQUE (gateway_refund_id),
    CONSTRAINT ck_refunds_reason CHECK (reason IN
        ('EARLY_CANCELLATION', 'PLATFORM_CANCELLATION', 'LATE_APPROVAL', 'ADMIN_ADJUSTMENT')),
    CONSTRAINT ck_refunds_cancellation CHECK (
        reason NOT IN ('EARLY_CANCELLATION', 'PLATFORM_CANCELLATION') OR booking_cancellation_id IS NOT NULL),
    CONSTRAINT ck_refunds_amount CHECK (amount_cop > 0),
    CONSTRAINT ck_refunds_status CHECK (status IN ('REQUESTED', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_refunds_completed CHECK ((status = 'COMPLETED') = (completed_at IS NOT NULL)),
    CONSTRAINT ck_refunds_failure CHECK (status <> 'FAILED' OR failure_reason IS NOT NULL),
    CONSTRAINT fk_refunds_payment FOREIGN KEY (payment_id)
        REFERENCES payments.payment_transactions (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_refunds_cancellation FOREIGN KEY (booking_cancellation_id)
        REFERENCES booking.booking_cancellations (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_refunds_created_by FOREIGN KEY (created_by)
        REFERENCES auth.users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX IF NOT EXISTS ix_refunds_payment ON payments.refunds (payment_id);
CREATE INDEX IF NOT EXISTS ix_refunds_cancellation ON payments.refunds (booking_cancellation_id);
CREATE INDEX IF NOT EXISTS ix_refunds_open ON payments.refunds (created_at) WHERE status IN ('REQUESTED', 'PROCESSING');
SELECT common.attach_updated_at('payments.refunds');

COMMENT ON TABLE  payments.refunds IS 'Órdenes de reembolso al turista: 85% en cancelación anticipada (RF-13), 100% en cancelación por plataforma (E4) o pago aprobado tras vencer el TTL (LATE_APPROVAL).';
COMMENT ON COLUMN payments.refunds.reason IS 'LATE_APPROVAL: la pasarela aprobó después de que la reserva expiró y liberó cupos; se devuelve el 100%.';

-- La suma de reembolsos no fallidos nunca supera el valor cobrado.
CREATE OR REPLACE FUNCTION payments.fn_refund_guard()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_payment_status  VARCHAR(20);
    v_amount          NUMERIC(14, 2);
    v_refunded        NUMERIC(14, 2);
BEGIN
    SELECT pt.status, pt.amount_cop INTO v_payment_status, v_amount
      FROM payments.payment_transactions pt
     WHERE pt.id = NEW.payment_id
       FOR UPDATE;

    IF v_payment_status IS DISTINCT FROM 'APPROVED' THEN
        RAISE EXCEPTION 'Solo se reembolsan pagos APPROVED (estado %)', v_payment_status
            USING ERRCODE = 'check_violation';
    END IF;

    IF TG_OP = 'UPDATE' THEN
        IF ROW(NEW.payment_id, NEW.amount_cop, NEW.reason)
           IS DISTINCT FROM ROW(OLD.payment_id, OLD.amount_cop, OLD.reason) THEN
            RAISE EXCEPTION 'Pago, monto y motivo del reembolso son inmutables' USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;

    SELECT COALESCE(sum(r.amount_cop), 0) INTO v_refunded
      FROM payments.refunds r
     WHERE r.payment_id = NEW.payment_id
       AND r.status <> 'FAILED'
       AND r.id <> NEW.id;

    IF NEW.status <> 'FAILED' AND v_refunded + NEW.amount_cop > v_amount THEN
        RAISE EXCEPTION 'Los reembolsos (% + %) superan el valor cobrado (%)', v_refunded, NEW.amount_cop, v_amount
            USING ERRCODE = 'check_violation';
    END IF;

    IF TG_OP = 'UPDATE' AND NEW.status = 'COMPLETED' AND NEW.completed_at IS NULL THEN
        NEW.completed_at := now();
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE TRIGGER trg_refund_guard
    BEFORE INSERT OR UPDATE ON payments.refunds
    FOR EACH ROW EXECUTE FUNCTION payments.fn_refund_guard();

-- -----------------------------------------------------------------------------
-- payments.payout_batches (lotes periódicos por asociación; F5)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS payments.payout_batches (
    id                 UUID          NOT NULL DEFAULT gen_random_uuid(),
    association_id     UUID          NOT NULL,
    payout_account_id  UUID          NOT NULL,
    status             VARCHAR(20)   NOT NULL DEFAULT 'OPEN',
    idempotency_key    VARCHAR(100)  NOT NULL,
    gateway_payout_id  VARCHAR(100),
    submitted_at       TIMESTAMPTZ,
    paid_at            TIMESTAMPTZ,
    failure_reason     TEXT,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by         UUID          DEFAULT common.current_actor(),
    CONSTRAINT pk_payout_batches PRIMARY KEY (id),
    CONSTRAINT uq_payout_batches_id_association UNIQUE (id, association_id),
    CONSTRAINT uq_payout_batches_idempotency UNIQUE (idempotency_key),
    CONSTRAINT uq_payout_batches_gateway UNIQUE (gateway_payout_id),
    CONSTRAINT ck_payout_batches_status CHECK (status IN ('OPEN', 'SUBMITTED', 'PAID', 'FAILED')),
    CONSTRAINT ck_payout_batches_submitted CHECK (status = 'OPEN' OR submitted_at IS NOT NULL),
    CONSTRAINT ck_payout_batches_paid CHECK ((status = 'PAID') = (paid_at IS NOT NULL)),
    CONSTRAINT ck_payout_batches_failed CHECK (status <> 'FAILED' OR failure_reason IS NOT NULL),
    CONSTRAINT fk_payout_batches_association FOREIGN KEY (association_id)
        REFERENCES catalog.community_associations (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    -- La cuenta destino debe pertenecer a la misma asociación.
    CONSTRAINT fk_payout_batches_account FOREIGN KEY (payout_account_id, association_id)
        REFERENCES payments.payout_accounts (id, association_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_payout_batches_created_by FOREIGN KEY (created_by)
        REFERENCES auth.users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_payout_batches_one_open
    ON payments.payout_batches (association_id) WHERE status = 'OPEN';
CREATE INDEX IF NOT EXISTS ix_payout_batches_association ON payments.payout_batches (association_id, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_payout_batches_account ON payments.payout_batches (payout_account_id, association_id);
SELECT common.attach_updated_at('payments.payout_batches');

COMMENT ON TABLE payments.payout_batches IS 'Lote de dispersión hacia una asociación (RF-19). Agrupa dispersiones RELEASED; su total se deriva de payments.disbursements.';

-- -----------------------------------------------------------------------------
-- payments.disbursements (porción comunitaria del 85% por pago; F4)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS payments.disbursements (
    id                   UUID               NOT NULL DEFAULT gen_random_uuid(),
    payment_id           UUID               NOT NULL,
    association_id       UUID               NOT NULL,
    amount_cop           common.cop_amount  NOT NULL,
    status               VARCHAR(20)        NOT NULL DEFAULT 'SCHEDULED',
    release_at           TIMESTAMPTZ        NOT NULL,
    released_at          TIMESTAMPTZ,
    payout_batch_id      UUID,
    cancellation_reason  TEXT,
    created_at           TIMESTAMPTZ        NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ        NOT NULL DEFAULT now(),
    created_by           UUID               DEFAULT common.current_actor(),
    CONSTRAINT pk_disbursements PRIMARY KEY (id),
    CONSTRAINT uq_disbursements_payment UNIQUE (payment_id),
    CONSTRAINT ck_disbursements_amount CHECK (amount_cop > 0),
    CONSTRAINT ck_disbursements_status CHECK (status IN ('SCHEDULED', 'ON_HOLD', 'RELEASED', 'PAID', 'CANCELLED')),
    CONSTRAINT ck_disbursements_released CHECK (status NOT IN ('RELEASED', 'PAID') OR released_at IS NOT NULL),
    CONSTRAINT ck_disbursements_paid CHECK (status <> 'PAID' OR payout_batch_id IS NOT NULL),
    CONSTRAINT ck_disbursements_cancelled CHECK (status <> 'CANCELLED' OR cancellation_reason IS NOT NULL),
    CONSTRAINT fk_disbursements_payment FOREIGN KEY (payment_id)
        REFERENCES payments.payment_transactions (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_disbursements_association FOREIGN KEY (association_id)
        REFERENCES catalog.community_associations (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    -- El lote debe ser de la misma asociación beneficiaria.
    CONSTRAINT fk_disbursements_batch FOREIGN KEY (payout_batch_id, association_id)
        REFERENCES payments.payout_batches (id, association_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_disbursements_created_by FOREIGN KEY (created_by)
        REFERENCES auth.users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX IF NOT EXISTS ix_disbursements_due
    ON payments.disbursements (release_at) WHERE status = 'SCHEDULED';
CREATE INDEX IF NOT EXISTS ix_disbursements_ready
    ON payments.disbursements (association_id) WHERE status = 'RELEASED' AND payout_batch_id IS NULL;
CREATE INDEX IF NOT EXISTS ix_disbursements_association ON payments.disbursements (association_id);
CREATE INDEX IF NOT EXISTS ix_disbursements_batch ON payments.disbursements (payout_batch_id, association_id);
SELECT common.attach_updated_at('payments.disbursements');

COMMENT ON TABLE  payments.disbursements IS 'Derecho de la asociación sobre un pago aprobado: CEIL(85% del bruto), en custodia (escrow) hasta su liberación (RF-12, RF-14, RF-19).';
COMMENT ON COLUMN payments.disbursements.association_id IS 'Beneficiaria al momento de la venta. Se guarda como hecho histórico: el anfitrión podría cambiar de asociación después.';
COMMENT ON COLUMN payments.disbursements.release_at IS 'Fin de la franja + payout_grace_hours (24 h supletorias, RF-19). Se libera antes si se sincroniza la asistencia.';
COMMENT ON COLUMN payments.disbursements.status IS 'SCHEDULED → RELEASED → PAID. CANCELLED si hubo reembolso anticipado; CANCELLED → RELEASED solo si la reserva quedó en SYNC_CONFLICT (protección comunitaria, RF-18).';

CREATE OR REPLACE FUNCTION payments.fn_disbursement_guard()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_payment_status  VARCHAR(20);
    v_booking_status  VARCHAR(30);
BEGIN
    IF TG_OP = 'INSERT' THEN
        SELECT pt.status INTO v_payment_status
          FROM payments.payment_transactions pt
         WHERE pt.id = NEW.payment_id;
        IF v_payment_status IS DISTINCT FROM 'APPROVED' THEN
            RAISE EXCEPTION 'Solo se programa dispersión de pagos APPROVED' USING ERRCODE = 'check_violation';
        END IF;
        IF NEW.status NOT IN ('SCHEDULED', 'ON_HOLD') THEN
            RAISE EXCEPTION 'Una dispersión nace SCHEDULED u ON_HOLD' USING ERRCODE = 'check_violation';
        END IF;
        RETURN NEW;
    END IF;

    IF ROW(NEW.payment_id, NEW.association_id, NEW.amount_cop)
       IS DISTINCT FROM ROW(OLD.payment_id, OLD.association_id, OLD.amount_cop) THEN
        RAISE EXCEPTION 'Pago, beneficiario y monto de la dispersión son inmutables' USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.status IS DISTINCT FROM OLD.status THEN
        IF (OLD.status, NEW.status) NOT IN (
            ('SCHEDULED', 'ON_HOLD'), ('SCHEDULED', 'RELEASED'), ('SCHEDULED', 'CANCELLED'),
            ('ON_HOLD', 'SCHEDULED'), ('ON_HOLD', 'RELEASED'), ('ON_HOLD', 'CANCELLED'),
            ('RELEASED', 'PAID'),
            ('CANCELLED', 'RELEASED')) THEN
            RAISE EXCEPTION 'Transición de dispersión no permitida: % -> %', OLD.status, NEW.status
                USING ERRCODE = 'check_violation';
        END IF;

        IF OLD.status = 'CANCELLED' THEN
            SELECT b.status INTO v_booking_status
              FROM payments.payment_transactions pt
              JOIN booking.bookings b ON b.id = pt.booking_id
             WHERE pt.id = NEW.payment_id;
            IF v_booking_status IS DISTINCT FROM 'SYNC_CONFLICT' THEN
                RAISE EXCEPTION 'Solo una reserva en SYNC_CONFLICT reactiva la dispersión comunitaria'
                    USING ERRCODE = 'check_violation';
            END IF;
            NEW.cancellation_reason := NULL;
        END IF;

        IF NEW.status = 'RELEASED' AND NEW.released_at IS NULL THEN
            NEW.released_at := now();
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE TRIGGER trg_disbursement_guard
    BEFORE INSERT OR UPDATE ON payments.disbursements
    FOR EACH ROW EXECUTE FUNCTION payments.fn_disbursement_guard();

-- -----------------------------------------------------------------------------
-- payments.settlement_receipts (comprobante contable por lote; append-only)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS payments.settlement_receipts (
    id                 UUID               NOT NULL DEFAULT gen_random_uuid(),
    receipt_number     BIGINT             GENERATED ALWAYS AS IDENTITY,
    payout_batch_id    UUID               NOT NULL,
    gross_amount_cop   common.cop_amount  NOT NULL,
    gateway_fee_cop    common.cop_amount  NOT NULL,
    platform_fee_cop   common.cop_amount  NOT NULL,
    net_community_cop  common.cop_amount  NOT NULL,
    pdf_s3_key         common.s3_key      NOT NULL,
    issued_at          TIMESTAMPTZ        NOT NULL DEFAULT now(),
    created_by         UUID               DEFAULT common.current_actor(),
    CONSTRAINT pk_settlement_receipts PRIMARY KEY (id),
    CONSTRAINT uq_settlement_receipts_number UNIQUE (receipt_number),
    CONSTRAINT uq_settlement_receipts_batch UNIQUE (payout_batch_id),
    CONSTRAINT ck_settlement_receipts_breakdown CHECK (gross_amount_cop = net_community_cop + platform_fee_cop),
    CONSTRAINT fk_settlement_receipts_batch FOREIGN KEY (payout_batch_id)
        REFERENCES payments.payout_batches (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_settlement_receipts_created_by FOREIGN KEY (created_by)
        REFERENCES auth.users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);
SELECT common.make_append_only('payments.settlement_receipts');

COMMENT ON TABLE  payments.settlement_receipts IS 'Comprobante contable digital de un lote de dispersión (RF-19). Documento legal: su desglose se congela y no se modifica.';
COMMENT ON COLUMN payments.settlement_receipts.receipt_number IS 'Consecutivo del comprobante.';
COMMENT ON COLUMN payments.settlement_receipts.platform_fee_cop IS 'Comisión de la plataforma (15%), de la cual se descuenta gateway_fee_cop.';

-- -----------------------------------------------------------------------------
-- Vista de totales por lote (el total no se almacena: se deriva).
-- -----------------------------------------------------------------------------
CREATE OR REPLACE VIEW payments.v_payout_batch_totals AS
SELECT pb.id                                    AS payout_batch_id,
       pb.association_id,
       pb.status,
       count(d.id)                              AS disbursement_count,
       COALESCE(sum(d.amount_cop), 0)           AS net_community_cop,
       COALESCE(sum(pt.amount_cop), 0)          AS gross_amount_cop,
       COALESCE(sum(pt.gateway_fee_cop), 0)     AS gateway_fee_cop
  FROM payments.payout_batches pb
  LEFT JOIN payments.disbursements d ON d.payout_batch_id = pb.id
  LEFT JOIN payments.payment_transactions pt ON pt.id = d.payment_id
 GROUP BY pb.id, pb.association_id, pb.status;

COMMENT ON VIEW payments.v_payout_batch_totals IS 'Totales derivados de cada lote: bruto, comisión de pasarela y neto comunitario.';
