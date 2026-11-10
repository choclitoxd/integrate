-- Legacy writers still work until the protected cut. During compatibility, the
-- application fills both representations in a single transaction.
ALTER TABLE auth.users
    ADD COLUMN doc_number_enc common.ciphertext,
    ADD COLUMN doc_key_version SMALLINT,
    ADD COLUMN document_index BYTEA,
    ADD COLUMN document_index_version SMALLINT,
    ADD COLUMN document_index_next BYTEA,
    ADD COLUMN document_index_next_version SMALLINT,
    ADD COLUMN phone_enc common.ciphertext,
    ADD COLUMN phone_key_version SMALLINT,
    ADD CONSTRAINT ck_users_document_protection CHECK (
        num_nonnulls(doc_number_enc, doc_key_version, document_index, document_index_version) IN (0, 4)
        AND (document_index IS NULL OR octet_length(document_index) = 32)
    ),
    ADD CONSTRAINT ck_users_document_next_index CHECK (
        num_nonnulls(document_index_next, document_index_next_version) IN (0, 2)
        AND (document_index_next IS NULL OR octet_length(document_index_next) = 32)
    ),
    ADD CONSTRAINT ck_users_phone_protection CHECK (num_nonnulls(phone_enc, phone_key_version) IN (0, 2));

-- The legacy unique constraint remains until the protected cut.
CREATE UNIQUE INDEX uq_users_document_index ON auth.users (document_index);
CREATE UNIQUE INDEX uq_users_document_index_next ON auth.users (document_index_next);

REVOKE SELECT (doc_number, phone) ON auth.users FROM integrate_readonly;

-- An old writer or legacy anonymization must never leave a stale protected
-- value. Such rows are picked up by the re-runnable backfill.
CREATE FUNCTION auth.invalidate_stale_user_identity() RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF (OLD.doc_type, OLD.doc_number) IS DISTINCT FROM (NEW.doc_type, NEW.doc_number)
       AND OLD.doc_number_enc IS NOT DISTINCT FROM NEW.doc_number_enc THEN
        NEW.doc_number_enc := NULL;
        NEW.doc_key_version := NULL;
        NEW.document_index := NULL;
        NEW.document_index_version := NULL;
        NEW.document_index_next := NULL;
        NEW.document_index_next_version := NULL;
    END IF;
    IF OLD.phone IS DISTINCT FROM NEW.phone AND OLD.phone_enc IS NOT DISTINCT FROM NEW.phone_enc THEN
        NEW.phone_enc := NULL;
        NEW.phone_key_version := NULL;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER invalidate_stale_user_identity
    BEFORE UPDATE OF doc_type, doc_number, phone ON auth.users
    FOR EACH ROW EXECUTE FUNCTION auth.invalidate_stale_user_identity();
