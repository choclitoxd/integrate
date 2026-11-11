package com.integrate.modules.auth.service.impl;

import com.integrate.common.audit.SensitiveAccessRecorder;
import com.integrate.common.crypto.HmacSha256;
import com.integrate.common.crypto.SensitiveDataCipher;
import com.integrate.common.crypto.SensitiveDataProperties;
import com.integrate.modules.auth.entity.DocType;
import com.integrate.modules.auth.entity.User;
import com.integrate.modules.auth.repository.UserRepository;
import com.integrate.security.ActorPropagatingTransactionManager;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Owns the boundary between the legacy columns and protected identity storage. */
@Service
@DependsOnDatabaseInitialization
public class IdentityProtection {
    enum Mode { LEGACY, DUAL, PROTECTED }

    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final SensitiveDataCipher cipher;
    private final Mode mode;
    private final short indexVersion;
    private final byte[] indexKey;
    private final Short nextIndexVersion;
    private final byte[] nextIndexKey;
    private final SensitiveDataProperties encryptionKeys;
    private final SensitiveAccessRecorder accessRecorder;

    public IdentityProtection(UserRepository users, JdbcTemplate jdbc, SensitiveDataCipher cipher,
                              SensitiveDataProperties encryptionKeys, SensitiveAccessRecorder accessRecorder,
                              @Value("${integrate.identity.mode:LEGACY}") Mode mode,
                              @Value("${integrate.identity.index-key-version:1}") short indexVersion,
                              @Value("${integrate.identity.index-key:}") String encodedIndexKey,
                              @Value("${integrate.identity.next-index-key-version:0}") short nextIndexVersion,
                              @Value("${integrate.identity.next-index-key:}") String encodedNextIndexKey) {
        this.users = users;
        this.jdbc = jdbc;
        this.cipher = cipher;
        this.encryptionKeys = encryptionKeys;
        this.accessRecorder = accessRecorder;
        this.mode = mode;
        this.indexVersion = indexVersion;
        this.indexKey = mode == Mode.LEGACY ? null : parseKey(indexVersion, encodedIndexKey);
        if (nextIndexVersion == 0 && encodedNextIndexKey.isEmpty()) {
            this.nextIndexVersion = null;
            this.nextIndexKey = null;
        } else {
            if (mode != Mode.PROTECTED || nextIndexVersion == indexVersion) {
                throw new IllegalStateException("Identity index rotation requires PROTECTED mode and an independent version and key");
            }
            this.nextIndexVersion = nextIndexVersion;
            this.nextIndexKey = parseKey(nextIndexVersion, encodedNextIndexKey);
            if (Arrays.equals(this.nextIndexKey, this.indexKey)) {
                throw new IllegalStateException("Identity index rotation requires a distinct key");
            }
        }
        if (mode != Mode.LEGACY) {
            for (String encodedEncryptionKey : encryptionKeys.keys().values()) {
                byte[] asHmac = parseKey((short) 1, encodedEncryptionKey);
                if (Arrays.equals(asHmac, indexKey) || Arrays.equals(asHmac, nextIndexKey)) {
                    throw new IllegalStateException("Identity index keys must be independent from encryption keys");
                }
            }
        }
    }

    @PostConstruct
    void verifyStorageMode() {
        boolean legacyColumnsPresent = Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = 'auth' AND table_name = 'users' AND column_name = 'doc_number')
                """, Boolean.class));
        if (legacyColumnsPresent == (mode == Mode.PROTECTED)) {
            throw new IllegalStateException("Identity mode is incompatible with database cutover state");
        }
        if (mode != Mode.LEGACY && Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM auth.users WHERE document_index_version <> ?)
                """, Boolean.class, indexVersion))) {
            throw new IllegalStateException("Configured identity index version does not match the active database index");
        }
        if (mode != Mode.LEGACY) {
            jdbc.query("""
                    SELECT DISTINCT doc_key_version AS key_version FROM auth.users WHERE doc_key_version IS NOT NULL
                    UNION SELECT DISTINCT phone_key_version AS key_version FROM auth.users WHERE phone_key_version IS NOT NULL
                    """, row -> {
                if (!encryptionKeys.keys().containsKey(row.getShort("key_version"))) {
                    throw new IllegalStateException("An encryption key required by stored identity is unavailable");
                }
            });
        }
    }

    private static byte[] parseKey(short indexVersion, String encodedIndexKey) {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(encodedIndexKey);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Identity index key must be base64 encoded");
        }
        if (indexVersion < 1 || key.length != 32) {
            throw new IllegalStateException("Identity index requires a positive version and a 32-byte key");
        }
        return key;
    }

    Optional<User> findByDocument(DocType type, String document) {
        if (mode != Mode.LEGACY) {
            Optional<User> protectedMatch = users.findByDocumentIndex(index(type, document));
            if (protectedMatch.isPresent() || mode == Mode.PROTECTED) {
                return protectedMatch;
            }
        }
        return jdbc.query("""
                SELECT id FROM auth.users WHERE doc_type = ? AND doc_number = ?
                """, (rs, row) -> rs.getObject("id", UUID.class), type.name(), document)
                .stream().findFirst().flatMap(users::findById);
    }

    boolean documentExists(DocType type, String document) {
        return findByDocument(type, document).isPresent();
    }

    void insertIdentity(User user) {
        if (mode != Mode.LEGACY) {
            protectDocument(user, user.getDocNumber());
            protectPhone(user, user.getPhone());
        }
        String columns = """
                id, email, password_hash, full_name, doc_type, status, credential_status,
                doc_number_enc, doc_key_version, document_index, document_index_version, phone_enc, phone_key_version,
                document_index_next, document_index_next_version
                """;
        Object[] protectedValues = {user.getId(), user.getEmail(), user.getPasswordHash(), user.getFullName(),
                user.getDocType().name(), user.getStatus().name(), user.getCredentialStatus().name(),
                user.getEncryptedDocument(), user.getDocumentKeyVersion(), user.getDocumentIndex(),
                user.getDocumentIndexVersion(), user.getEncryptedPhone(), user.getPhoneKeyVersion(),
                user.getNextDocumentIndex(), user.getNextDocumentIndexVersion()};
        String values = "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?";
        int inserted;
        if (mode == Mode.PROTECTED) {
            inserted = jdbc.update("INSERT INTO auth.users (" + columns + ") VALUES (" + values
                    + ") ON CONFLICT DO NOTHING", protectedValues);
        } else {
            Object[] allValues = Arrays.copyOf(protectedValues, protectedValues.length + 2);
            allValues[protectedValues.length] = user.getDocNumber();
            allValues[protectedValues.length + 1] = user.getPhone();
            inserted = jdbc.update("INSERT INTO auth.users (" + columns + ", doc_number, phone) VALUES ("
                    + values + ", ?, ?) ON CONFLICT DO NOTHING", allValues);
        }
        if (inserted == 0) {
            throw AuthErrors.userAlreadyExists();
        }
    }

    /** Cada lectura del celular queda en la bitácora de accesos sensibles (ADR-0046 §2 y §5, ADR-0035). */
    public String readPhone(User user) {
        String phone;
        if (mode != Mode.LEGACY && user.getEncryptedPhone() != null) {
            phone = cipher.decrypt(user.getEncryptedPhone(), aad(user, "phone"), user.getPhoneKeyVersion());
        } else {
            requireLegacyFallback();
            phone = jdbc.queryForObject("SELECT phone FROM auth.users WHERE id = ?", String.class, user.getId());
        }
        recordRead(user, "phone");
        return phone;
    }

    /** Cada lectura del documento queda en la bitácora de accesos sensibles (ADR-0046 §2 y §5, ADR-0035). */
    String readDocument(User user) {
        String document;
        if (mode != Mode.LEGACY && user.getEncryptedDocument() != null) {
            document = cipher.decrypt(user.getEncryptedDocument(), aad(user, "doc_number"), user.getDocumentKeyVersion());
        } else {
            requireLegacyFallback();
            document = jdbc.queryForObject("SELECT doc_number FROM auth.users WHERE id = ?", String.class, user.getId());
        }
        recordRead(user, "doc_number");
        return document;
    }

    /**
     * Actor = usuario autenticado de la petición; {@code null} en procesos del sistema (migración, correos). La
     * fila exige una transacción de escritura: las lecturas llamadas desde transacciones de solo lectura deben
     * abrirse sin {@code readOnly}.
     */
    private void recordRead(User user, String field) {
        String subject = user.getId().toString();
        accessRecorder.record(new SensitiveAccessRecorder.Access("auth", "users", subject, "IDENTITY_READ",
                List.of(field), List.of(subject), ActorPropagatingTransactionManager.currentActor(), null, null));
    }

    /** The caller holds the row lock for the duration of the profile transaction. */
    public void changePhone(User user, String phone) {
        if (mode == Mode.PROTECTED) {
            protectPhone(user, phone);
        } else if (mode == Mode.DUAL) {
            protectPhone(user, phone);
            jdbc.update("UPDATE auth.users SET phone = ?, phone_enc = ?, phone_key_version = ? WHERE id = ?",
                    phone, user.getEncryptedPhone(), user.getPhoneKeyVersion(), user.getId());
        } else {
            jdbc.update("UPDATE auth.users SET phone = ? WHERE id = ?", phone, user.getId());
        }
    }

    /** The caller holds the row lock for the duration of the profile transaction. */
    public void changeDocument(User user, DocType docType, String docNumber) {
        user.updateDocType(docType);
        if (mode == Mode.PROTECTED) {
            protectDocument(user, docNumber);
        } else if (mode == Mode.DUAL) {
            protectDocument(user, docNumber);
            jdbc.update("""
                    UPDATE auth.users
                    SET doc_type = ?, doc_number = ?, doc_number_enc = ?, doc_key_version = ?,
                        document_index = ?, document_index_version = ?, document_index_next = ?,
                        document_index_next_version = ?
                    WHERE id = ?
                    """,
                    docType.name(), docNumber, user.getEncryptedDocument(), user.getDocumentKeyVersion(),
                    user.getDocumentIndex(), user.getDocumentIndexVersion(), user.getNextDocumentIndex(),
                    user.getNextDocumentIndexVersion(), user.getId());
        } else {
            jdbc.update("UPDATE auth.users SET doc_type = ?, doc_number = ? WHERE id = ?",
                    docType.name(), docNumber, user.getId());
        }
    }

    void protectDocument(User user, String document) {
        short version = cipher.currentKeyVersion();
        user.protectDocument(cipher.encrypt(document, aad(user, "doc_number"), version),
                version, index(user.getDocType(), document), indexVersion);
        if (nextIndexKey != null) {
            user.prepareNextDocumentIndex(nextIndex(user.getDocType(), document), nextIndexVersion);
        }
    }

    void protectPhone(User user, String phone) {
        short version = cipher.currentKeyVersion();
        user.protectPhone(cipher.encrypt(phone, aad(user, "phone"), version), version);
    }

    byte[] index(DocType type, String document) {
        return hmac(indexKey, type, document);
    }

    byte[] nextIndex(DocType type, String document) {
        return hmac(nextIndexKey, type, document);
    }

    Short nextIndexVersion() {
        return nextIndexVersion;
    }

    short encryptionVersion() {
        return cipher.currentKeyVersion();
    }

    private static byte[] hmac(byte[] key, DocType type, String document) {
        // DocType and the legacy domain cannot contain the separator.
        return HmacSha256.mac(key, ("auth.users.document\0" + type.name() + "\0" + document)
                .getBytes(StandardCharsets.UTF_8));
    }

    Mode mode() {
        return mode;
    }

    short indexVersion() {
        return indexVersion;
    }

    private void requireLegacyFallback() {
        if (mode == Mode.PROTECTED) {
            throw new IllegalStateException("Protected identity is incomplete");
        }
    }

    private static String aad(User user, String field) {
        return "auth.users:" + field + ":" + user.getId();
    }
}
