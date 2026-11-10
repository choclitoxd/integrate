-- Operador: detener escritores y verificar equivalencia con el proceso DUAL
-- antes de ejecutar este archivo. Ejecutar con el dueño del esquema.
BEGIN;

SET LOCAL lock_timeout = '5s';
LOCK TABLE auth.users IN ACCESS EXCLUSIVE MODE;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM auth.users
        WHERE doc_number IS NULL OR phone IS NULL OR doc_number_enc IS NULL OR phone_enc IS NULL
    ) OR (SELECT count(DISTINCT document_index_version) FROM auth.users) > 1 THEN
        RAISE EXCEPTION 'Identity cut rejected: incomplete coverage or mixed index versions';
    END IF;
END;
$$;

DROP TRIGGER invalidate_stale_user_identity ON auth.users;
DROP FUNCTION auth.invalidate_stale_user_identity();
DROP FUNCTION auth.anonymize_user(UUID);

ALTER TABLE auth.users
    ALTER COLUMN doc_number_enc SET NOT NULL,
    ALTER COLUMN doc_key_version SET NOT NULL,
    ALTER COLUMN document_index SET NOT NULL,
    ALTER COLUMN document_index_version SET NOT NULL,
    ALTER COLUMN phone_enc SET NOT NULL,
    ALTER COLUMN phone_key_version SET NOT NULL,
    DROP CONSTRAINT uq_users_document,
    DROP COLUMN doc_number,
    DROP COLUMN phone;

-- El proceso autorizado prepara los sustitutos fuera de la base. La función
-- aplica la supresión y la revocación en la misma transacción.
CREATE FUNCTION auth.anonymize_user(
    p_user_id UUID, p_doc_enc BYTEA, p_doc_version SMALLINT,
    p_index BYTEA, p_index_version SMALLINT, p_phone_enc BYTEA, p_phone_version SMALLINT,
    p_next_index BYTEA, p_next_version SMALLINT
) RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
    UPDATE auth.users
       SET email = 'anon+' || id::text || '@anonymized.invalid',
           password_hash = '$2a$12$anonymized.account.cannot.login',
           full_name = 'Titular anonimizado',
           doc_type = 'PA',
           doc_number_enc = p_doc_enc,
           doc_key_version = p_doc_version,
           document_index = p_index,
           document_index_version = p_index_version,
           document_index_next = p_next_index,
           document_index_next_version = p_next_version,
           phone_enc = p_phone_enc,
           phone_key_version = p_phone_version,
           status = 'ANONYMIZED',
           anonymized_at = now(),
           deleted_at = COALESCE(deleted_at, now())
     WHERE id = p_user_id AND status <> 'ANONYMIZED';

    UPDATE auth.refresh_tokens
       SET revoked_at = now(), revoke_reason = 'ADMIN'
     WHERE user_id = p_user_id AND revoked_at IS NULL;
END;
$$;

REVOKE EXECUTE ON FUNCTION auth.anonymize_user(UUID, BYTEA, SMALLINT, BYTEA, SMALLINT, BYTEA, SMALLINT, BYTEA, SMALLINT)
    FROM PUBLIC;
GRANT EXECUTE ON FUNCTION auth.anonymize_user(UUID, BYTEA, SMALLINT, BYTEA, SMALLINT, BYTEA, SMALLINT, BYTEA, SMALLINT)
    TO integrate_app;

COMMIT;
