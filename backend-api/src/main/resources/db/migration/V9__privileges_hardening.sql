-- =============================================================================
-- V9 · Endurecimiento de privilegios (A5, I1, RF-20).
-- Los privilegios por defecto de V1 otorgan SELECT/INSERT/UPDATE a integrate_app
-- y SELECT a integrate_readonly. Aquí se recortan y se agregan las excepciones.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Tablas append-only: la aplicación solo inserta y lee.
--    Los triggers de V2–V8 bloquean además UPDATE/DELETE para cualquier rol.
-- -----------------------------------------------------------------------------
REVOKE UPDATE ON
    auth.habeas_data_consents,
    catalog.experience_status_history,
    booking.booking_status_history,
    booking.booking_cancellations,
    payments.settlement_receipts,
    offline.roster_downloads,
    offline.offline_validations
FROM integrate_app;

-- Bitácora: ni la aplicación ni reportes acceden a chain_head. Solo el trigger
-- SECURITY DEFINER (cuyo dueño es integrate_owner) la actualiza.
REVOKE ALL ON ALL TABLES IN SCHEMA audit FROM integrate_app, integrate_readonly;
GRANT SELECT, INSERT ON audit.audit_trail TO integrate_app;
GRANT SELECT ON audit.audit_trail TO integrate_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA audit REVOKE UPDATE ON TABLES FROM integrate_app;

-- ensure_year_partition es DDL: solo el owner (job anual o migración).
REVOKE EXECUTE ON FUNCTION audit.ensure_year_partition(INTEGER) FROM PUBLIC;

-- -----------------------------------------------------------------------------
-- 2. Catálogos de referencia gobernados exclusivamente por migraciones.
-- -----------------------------------------------------------------------------
REVOKE INSERT, UPDATE ON
    auth.roles,
    catalog.departments,
    catalog.municipalities
FROM integrate_app;

-- -----------------------------------------------------------------------------
-- 3. Únicas tablas con borrado físico permitido (datos operativos, no legales).
-- -----------------------------------------------------------------------------
GRANT DELETE ON
    auth.login_attempts,              -- depuración de intentos con más de 1 año
    auth.refresh_tokens,              -- depuración de tokens expirados
    catalog.experience_categories     -- tabla puente N:M sin valor histórico
TO integrate_app;

-- -----------------------------------------------------------------------------
-- 4. Lectura para reportes: sin secretos de autenticación.
--    Las columnas *_enc ya son ilegibles sin la llave de la aplicación.
-- -----------------------------------------------------------------------------
REVOKE SELECT ON auth.users, auth.refresh_tokens, auth.login_attempts FROM integrate_readonly;
GRANT SELECT (id, email, full_name, doc_type, doc_number, phone, status, locked_until,
              last_login_at, anonymized_at, created_at, updated_at, created_by, deleted_at)
    ON auth.users TO integrate_readonly;
REVOKE SELECT ON payments.payment_webhook_events FROM integrate_readonly;

-- -----------------------------------------------------------------------------
-- 5. Funciones administrativas de supresión: solo la aplicación.
-- -----------------------------------------------------------------------------
REVOKE EXECUTE ON FUNCTION auth.anonymize_user(UUID) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION catalog.anonymize_host_sensitive_data(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION auth.anonymize_user(UUID) TO integrate_app;
GRANT EXECUTE ON FUNCTION catalog.anonymize_host_sensitive_data(UUID) TO integrate_app;
