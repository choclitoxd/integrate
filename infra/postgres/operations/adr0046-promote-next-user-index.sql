-- Operador: detener todos los escritores y ejecutar VERIFY_ROTATION antes.
BEGIN;

SET LOCAL lock_timeout = '5s';
LOCK TABLE auth.users IN ACCESS EXCLUSIVE MODE;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'auth' AND table_name = 'users' AND column_name = 'doc_number')
       OR EXISTS (SELECT 1 FROM auth.users
                  WHERE document_index_next IS NULL
                     OR document_index_next_version = document_index_version)
       OR (SELECT count(DISTINCT document_index_next_version) FROM auth.users) > 1 THEN
        RAISE EXCEPTION 'Identity index promotion rejected: incompatible phase or incomplete next index';
    END IF;
END;
$$;

ALTER TABLE auth.users DROP CONSTRAINT ck_users_document_protection;
ALTER TABLE auth.users DROP CONSTRAINT ck_users_document_next_index;
ALTER TABLE auth.users DROP COLUMN document_index, DROP COLUMN document_index_version;
ALTER TABLE auth.users RENAME COLUMN document_index_next TO document_index;
ALTER TABLE auth.users RENAME COLUMN document_index_next_version TO document_index_version;
ALTER INDEX auth.uq_users_document_index_next RENAME TO uq_users_document_index;

ALTER TABLE auth.users
    ALTER COLUMN document_index SET NOT NULL,
    ALTER COLUMN document_index_version SET NOT NULL,
    ADD CONSTRAINT ck_users_document_protection CHECK (octet_length(document_index) = 32),
    ADD COLUMN document_index_next BYTEA,
    ADD COLUMN document_index_next_version SMALLINT,
    ADD CONSTRAINT ck_users_document_next_index CHECK (
        num_nonnulls(document_index_next, document_index_next_version) IN (0, 2)
        AND (document_index_next IS NULL OR octet_length(document_index_next) = 32)
    );

CREATE UNIQUE INDEX uq_users_document_index_next ON auth.users (document_index_next);

COMMIT;
