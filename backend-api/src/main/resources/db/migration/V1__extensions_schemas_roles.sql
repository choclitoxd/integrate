-- =============================================================================
-- V1 · Fundaciones: roles de grupo, extensiones, esquemas, dominios y
--      funciones comunes reutilizadas por todos los módulos.
-- Ejecuta: integrate_owner (Flyway). PostgreSQL 16.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Roles de grupo (A5). En producción los crea infra/postgres/init/00_bootstrap.sql;
--    este bloque solo los crea si faltan y el usuario tiene CREATEROLE
--    (p. ej. Testcontainers con superusuario).
-- -----------------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'integrate_app') THEN
        CREATE ROLE integrate_app NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'integrate_readonly') THEN
        CREATE ROLE integrate_readonly NOLOGIN;
    END IF;
END
$$;

-- -----------------------------------------------------------------------------
-- 2. Extensiones (ambas "trusted": no requieren superusuario).
--    gen_random_uuid() y sha256() son nativas desde PG13/PG11: no se usa pgcrypto.
--    El cifrado AES-256-GCM se hace en la aplicación (B4).
-- -----------------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS pg_trgm;     -- búsqueda difusa del catálogo (ILIKE / similarity)
CREATE EXTENSION IF NOT EXISTS btree_gist;  -- restricciones EXCLUDE con igualdad sobre UUID

-- -----------------------------------------------------------------------------
-- 3. Esquemas por módulo (A2) + common (utilidades) + notification (outbox).
-- -----------------------------------------------------------------------------
CREATE SCHEMA IF NOT EXISTS common;
CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS catalog;
CREATE SCHEMA IF NOT EXISTS booking;
CREATE SCHEMA IF NOT EXISTS payments;
CREATE SCHEMA IF NOT EXISTS voucher;
CREATE SCHEMA IF NOT EXISTS offline;
CREATE SCHEMA IF NOT EXISTS audit;
CREATE SCHEMA IF NOT EXISTS notification;

COMMENT ON SCHEMA common       IS 'Dominios y funciones utilitarias compartidas por todos los módulos.';
COMMENT ON SCHEMA auth         IS 'com.integrate.auth: identidades, RBAC, consentimiento Habeas Data (Ley 1581/2012) y sesiones.';
COMMENT ON SCHEMA catalog      IS 'com.integrate.catalog: asociaciones, anfitriones, gestores de enlace, experiencias y franjas.';
COMMENT ON SCHEMA booking      IS 'com.integrate.booking: reservas con bloqueo pesimista de aforo y participantes.';
COMMENT ON SCHEMA payments     IS 'com.integrate.payments: pagos, reembolsos, dispersión 85/15 y lotes de pago a asociaciones.';
COMMENT ON SCHEMA voucher      IS 'com.integrate.voucher: vouchers criptográficos (JWT firmado) y llaves públicas de verificación.';
COMMENT ON SCHEMA offline      IS 'com.integrate.offline: dispositivos de gestores, padrones, sincronización diferida e incidentes.';
COMMENT ON SCHEMA audit        IS 'com.integrate.audit: bitácora inmutable encadenada criptográficamente (RF-20, RNF-09).';
COMMENT ON SCHEMA notification IS 'com.integrate.notification: transactional outbox de correos/WhatsApp/SMS.';

GRANT USAGE ON SCHEMA common, auth, catalog, booking, payments, voucher, offline, audit, notification
    TO integrate_app, integrate_readonly;

-- Privilegios por defecto para todo objeto que integrate_owner cree después de V1.
-- No se otorga DELETE ni TRUNCATE (A4: sin borrado físico). V9 ajusta excepciones.
ALTER DEFAULT PRIVILEGES IN SCHEMA common, auth, catalog, booking, payments, voucher, offline, audit, notification
    GRANT SELECT, INSERT, UPDATE ON TABLES TO integrate_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA common, auth, catalog, booking, payments, voucher, offline, audit, notification
    GRANT SELECT ON TABLES TO integrate_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA common, auth, catalog, booking, payments, voucher, offline, audit, notification
    GRANT USAGE, SELECT ON SEQUENCES TO integrate_app;

-- -----------------------------------------------------------------------------
-- 4. Dominios reutilizables
-- -----------------------------------------------------------------------------
CREATE DOMAIN common.doc_type AS VARCHAR(3)
    CHECK (VALUE IN ('CC', 'CE', 'PA', 'PPT', 'TI'));
COMMENT ON DOMAIN common.doc_type IS
    'Tipo de documento de identidad: CC cédula de ciudadanía, CE cédula de extranjería, PA pasaporte, PPT permiso por protección temporal, TI tarjeta de identidad.';

CREATE DOMAIN common.doc_number AS VARCHAR(20)
    CHECK (VALUE ~ '^[A-Za-z0-9]{3,20}$');
COMMENT ON DOMAIN common.doc_number IS 'Número de documento alfanumérico sin puntos ni guiones (3–20 caracteres).';

CREATE DOMAIN common.phone_e164 AS VARCHAR(16)
    CHECK (VALUE ~ '^\+[1-9][0-9]{6,14}$');
COMMENT ON DOMAIN common.phone_e164 IS 'Teléfono en formato internacional E.164 (p. ej. +573001234567).';

CREATE DOMAIN common.email AS VARCHAR(254)
    CHECK (VALUE ~* '^[^@\s]+@[^@\s]+\.[^@\s]+$');
COMMENT ON DOMAIN common.email IS 'Correo electrónico (validación sintáctica básica; la verificación real es por enlace).';

CREATE DOMAIN common.cop_amount AS NUMERIC(14, 2)
    CHECK (VALUE >= 0 AND VALUE = trunc(VALUE));
COMMENT ON DOMAIN common.cop_amount IS 'Monto en pesos colombianos: no negativo y sin centavos (D4).';

CREATE DOMAIN common.percentage AS NUMERIC(5, 2)
    CHECK (VALUE >= 0 AND VALUE <= 100);
COMMENT ON DOMAIN common.percentage IS 'Porcentaje entre 0 y 100 con dos decimales.';

CREATE DOMAIN common.sha256_hex AS CHAR(64)
    CHECK (VALUE ~ '^[0-9a-f]{64}$');
COMMENT ON DOMAIN common.sha256_hex IS 'Hash SHA-256 en hexadecimal minúscula.';

CREATE DOMAIN common.ciphertext AS BYTEA
    CHECK (octet_length(VALUE) >= 28);
COMMENT ON DOMAIN common.ciphertext IS
    'Dato cifrado en la aplicación con AES-256-GCM (B4): IV de 12 bytes || texto cifrado || tag de 16 bytes. La llave nunca llega a la base de datos.';

CREATE DOMAIN common.s3_key AS VARCHAR(1024)
    CHECK (VALUE ~ '^[A-Za-z0-9!_.*''()/-]+$');
COMMENT ON DOMAIN common.s3_key IS
    'Clave de objeto en AWS S3 (no la URL firmada, que expira; G3). No debe contener datos personales.';

-- -----------------------------------------------------------------------------
-- 5. Funciones comunes
-- -----------------------------------------------------------------------------

-- Actor de la transacción. El backend ejecuta, al inicio de cada transacción:
--   SET LOCAL integrate.actor_id = '<uuid>';  [SET LOCAL integrate.status_reason = '...';]
CREATE OR REPLACE FUNCTION common.current_actor()
RETURNS UUID
LANGUAGE sql
STABLE
AS $$
    SELECT NULLIF(current_setting('integrate.actor_id', true), '')::uuid
$$;
COMMENT ON FUNCTION common.current_actor() IS
    'Devuelve el UUID del usuario que ejecuta la transacción (variable de sesión integrate.actor_id) o NULL si es un proceso del sistema.';

CREATE OR REPLACE FUNCTION common.current_reason()
RETURNS TEXT
LANGUAGE sql
STABLE
AS $$
    SELECT NULLIF(current_setting('integrate.status_reason', true), '')
$$;
COMMENT ON FUNCTION common.current_reason() IS
    'Motivo de negocio de la transacción en curso (variable integrate.status_reason), usado por los historiales de estado.';

CREATE OR REPLACE FUNCTION common.set_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW IS DISTINCT FROM OLD THEN
        NEW.updated_at := now();
    END IF;
    RETURN NEW;
END;
$$;
COMMENT ON FUNCTION common.set_updated_at() IS 'Trigger BEFORE UPDATE: actualiza updated_at cuando la fila cambia.';

CREATE OR REPLACE FUNCTION common.forbid_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'La tabla %.% es de solo inserción (append-only): % no está permitido',
        TG_TABLE_SCHEMA, TG_TABLE_NAME, TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$;
COMMENT ON FUNCTION common.forbid_mutation() IS 'Trigger que bloquea UPDATE, DELETE y TRUNCATE en tablas append-only (RF-20).';

CREATE OR REPLACE FUNCTION common.attach_updated_at(p_table REGCLASS)
RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
    EXECUTE format(
        'CREATE OR REPLACE TRIGGER trg_set_updated_at BEFORE UPDATE ON %s '
        'FOR EACH ROW EXECUTE FUNCTION common.set_updated_at()', p_table);
END;
$$;
COMMENT ON FUNCTION common.attach_updated_at(REGCLASS) IS 'Instala el trigger estándar de updated_at en la tabla indicada.';

CREATE OR REPLACE FUNCTION common.make_append_only(p_table REGCLASS)
RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
    EXECUTE format(
        'CREATE OR REPLACE TRIGGER trg_forbid_update_delete BEFORE UPDATE OR DELETE ON %s '
        'FOR EACH ROW EXECUTE FUNCTION common.forbid_mutation()', p_table);
    EXECUTE format(
        'CREATE OR REPLACE TRIGGER trg_forbid_truncate BEFORE TRUNCATE ON %s '
        'FOR EACH STATEMENT EXECUTE FUNCTION common.forbid_mutation()', p_table);
END;
$$;
COMMENT ON FUNCTION common.make_append_only(REGCLASS) IS 'Convierte la tabla en append-only instalando los triggers de bloqueo.';
