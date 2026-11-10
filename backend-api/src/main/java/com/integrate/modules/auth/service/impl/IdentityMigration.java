package com.integrate.modules.auth.service.impl;

import com.integrate.modules.auth.entity.User;
import com.integrate.modules.auth.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.security.MessageDigest;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class IdentityMigration {
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final IdentityProtection protection;
    private final EntityManager entityManager;

    @Transactional
    public int backfillBatch(int batchSize) {
        requireDual();
        // Keep the source row locked from snapshot through commit. Every writer
        // either completes before this read or sees the newly protected row.
        List<LegacyIdentity> batch = jdbc.query("""
                SELECT id, doc_number, phone FROM auth.users
                WHERE doc_number_enc IS NULL OR phone_enc IS NULL
                ORDER BY id LIMIT ? FOR UPDATE SKIP LOCKED
                """, (rs, row) -> new LegacyIdentity(rs.getObject("id", UUID.class),
                rs.getString("doc_number"), rs.getString("phone")), batchSize);
        for (LegacyIdentity source : batch) {
            User user = detachedUser(source.id());
            protection.protectDocument(user, source.document());
            protection.protectPhone(user, source.phone());
            saveProtectedValues(user);
        }
        return batch.size();
    }

    @Transactional(readOnly = true)
    public long missing() {
        requireDual();
        return jdbc.queryForObject("""
                SELECT count(*) FROM auth.users WHERE doc_number_enc IS NULL OR phone_enc IS NULL
                """, Long.class);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<Coverage> verify(int batchSize) {
        requireDual();
        UUID cursor = null;
        long checked = 0;
        while (true) {
            List<LegacyIdentity> batch = verificationBatch(cursor, batchSize);
            if (batch.isEmpty()) {
                break;
            }
            for (LegacyIdentity source : batch) {
                User user = detachedUser(source.id());
                if (user.getEncryptedDocument() == null || user.getEncryptedPhone() == null
                        || !Objects.equals(user.getDocumentIndexVersion(), protection.indexVersion())
                        || !Objects.equals(source.document(), protection.readDocument(user))
                        || !Objects.equals(source.phone(), protection.readPhone(user))
                        || !MessageDigest.isEqual(protection.index(user.getDocType(), source.document()),
                                user.getDocumentIndex())) {
                    throw new IllegalStateException("Protected identity verification failed");
                }
                checked++;
            }
            cursor = batch.getLast().id();
        }
        List<Coverage> coverage = jdbc.query("""
                SELECT status, credential_status, deleted_at IS NOT NULL AS deleted, count(*) AS rows
                FROM auth.users GROUP BY status, credential_status, deleted ORDER BY 1, 2, 3
                """, (rs, row) -> new Coverage(rs.getString("status"), rs.getString("credential_status"),
                rs.getBoolean("deleted"), rs.getLong("rows")));
        if (coverage.stream().mapToLong(Coverage::rows).sum() != checked) {
            throw new IllegalStateException("Protected identity row count does not reconcile");
        }
        return coverage;
    }

    @Transactional
    public int rotateBatch(int batchSize) {
        requireProtected();
        List<UUID> ids = jdbc.query("""
                SELECT id FROM auth.users
                WHERE doc_key_version <> ? OR phone_key_version <> ?
                   OR (CAST(? AS smallint) IS NOT NULL AND document_index_next_version IS DISTINCT FROM ?)
                ORDER BY id LIMIT ? FOR UPDATE SKIP LOCKED
                """, (rs, row) -> rs.getObject("id", UUID.class), protection.encryptionVersion(),
                protection.encryptionVersion(), protection.nextIndexVersion(), protection.nextIndexVersion(), batchSize);
        for (UUID id : ids) {
            User user = detachedUser(id);
            String document = protection.readDocument(user);
            if (user.getDocumentKeyVersion() != protection.encryptionVersion()) {
                protection.protectDocument(user, document);
            } else if (protection.nextIndexVersion() != null) {
                user.prepareNextDocumentIndex(protection.nextIndex(user.getDocType(), document),
                        protection.nextIndexVersion());
            }
            if (user.getPhoneKeyVersion() != protection.encryptionVersion()) {
                protection.protectPhone(user, protection.readPhone(user));
            }
            saveProtectedValues(user);
        }
        return ids.size();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public long verifyRotation(int batchSize) {
        requireProtected();
        UUID cursor = null;
        long checked = 0;
        while (true) {
            List<UUID> ids = jdbc.query("""
                    SELECT id FROM auth.users WHERE CAST(? AS uuid) IS NULL OR id > CAST(? AS uuid)
                    ORDER BY id LIMIT ?
                    """, (rs, row) -> rs.getObject("id", UUID.class), cursor, cursor, batchSize);
            if (ids.isEmpty()) {
                return checked;
            }
            for (UUID id : ids) {
                User user = detachedUser(id);
                String document = protection.readDocument(user);
                protection.readPhone(user);
                boolean invalidNextIndex = protection.nextIndexVersion() != null
                        && (!Objects.equals(user.getNextDocumentIndexVersion(), protection.nextIndexVersion())
                            || !MessageDigest.isEqual(user.getNextDocumentIndex(),
                                    protection.nextIndex(user.getDocType(), document)));
                if (user.getDocumentKeyVersion() != protection.encryptionVersion()
                        || user.getPhoneKeyVersion() != protection.encryptionVersion()
                        || !Objects.equals(user.getDocumentIndexVersion(), protection.indexVersion())
                        || !MessageDigest.isEqual(user.getDocumentIndex(), protection.index(user.getDocType(), document))
                        || invalidNextIndex) {
                    throw new IllegalStateException("Protected identity rotation verification failed");
                }
                checked++;
            }
            cursor = ids.getLast();
        }
    }

    @Transactional
    public void anonymize(UUID id) {
        User user = users.findByIdForUpdate(id).orElseThrow(() -> new IllegalStateException("Identity target is unavailable"));
        if (protection.mode() != IdentityProtection.Mode.PROTECTED) {
            jdbc.query("SELECT auth.anonymize_user(?)", rs -> { }, id);
            if (protection.mode() == IdentityProtection.Mode.DUAL) {
                protectAnonymizedLegacyIdentity(id);
            }
            return;
        }
        User replacement = User.identityForAnonymization(id);
        protection.protectDocument(replacement, replacement.getDocNumber());
        protection.protectPhone(replacement, replacement.getPhone());
        jdbc.query("""
                SELECT auth.anonymize_user(?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, rs -> { }, user.getId(), replacement.getEncryptedDocument(), replacement.getDocumentKeyVersion(),
                replacement.getDocumentIndex(), replacement.getDocumentIndexVersion(), replacement.getEncryptedPhone(),
                replacement.getPhoneKeyVersion(), replacement.getNextDocumentIndex(), replacement.getNextDocumentIndexVersion());
    }

    private void protectAnonymizedLegacyIdentity(UUID id) {
        User replacement = User.identityForAnonymization(id);
        protection.protectDocument(replacement, replacement.getDocNumber());
        protection.protectPhone(replacement, replacement.getPhone());
        saveProtectedValues(replacement);
    }

    private void saveProtectedValues(User user) {
        jdbc.update("""
                UPDATE auth.users SET doc_number_enc = ?, doc_key_version = ?, document_index = ?,
                  document_index_version = ?, phone_enc = ?, phone_key_version = ?,
                  document_index_next = ?, document_index_next_version = ?
                WHERE id = ?
                """, user.getEncryptedDocument(), user.getDocumentKeyVersion(), user.getDocumentIndex(),
                user.getDocumentIndexVersion(), user.getEncryptedPhone(), user.getPhoneKeyVersion(),
                user.getNextDocumentIndex(), user.getNextDocumentIndexVersion(), user.getId());
    }

    private User detachedUser(UUID id) {
        User user = users.findById(id).orElseThrow();
        entityManager.detach(user);
        return user;
    }

    @Transactional(readOnly = true)
    public long countLegacyAliases() {
        return jdbc.queryForObject("""
                SELECT count(*) FROM auth.users
                WHERE lower(email) ~ '^(cc|ce|pa|ppt|ti)[a-z0-9]{3,20}@comunidad[.]integrate[.]co$'
                """, Long.class);
    }

    @Transactional
    public void replaceLegacyAlias(UUID id, String alias) {
        if (!alias.matches("h-[a-hj-np-z2-9]{10}@comunidad[.]integrate[.]co")) {
            throw new IllegalStateException("Identity target alias does not match the approved format");
        }
        int changed = jdbc.update("""
                UPDATE auth.users SET email = ?
                WHERE id = ? AND lower(email) ~ '^(cc|ce|pa|ppt|ti)[a-z0-9]{3,20}@comunidad[.]integrate[.]co$'
                """, alias, id);
        if (changed != 1) {
            throw new IllegalStateException("Identity target has no legacy synthetic alias");
        }
    }

    private List<LegacyIdentity> verificationBatch(UUID cursor, int batchSize) {
        return jdbc.query("""
                SELECT id, doc_number, phone FROM auth.users
                WHERE CAST(? AS uuid) IS NULL OR id > CAST(? AS uuid)
                ORDER BY id LIMIT ?
                """, (rs, row) -> new LegacyIdentity(rs.getObject("id", UUID.class),
                rs.getString("doc_number"), rs.getString("phone")), cursor, cursor, batchSize);
    }

    private void requireDual() {
        if (protection.mode() != IdentityProtection.Mode.DUAL) {
            throw new IllegalStateException("Identity backfill and legacy verification require DUAL mode");
        }
    }

    private void requireProtected() {
        if (protection.mode() != IdentityProtection.Mode.PROTECTED) {
            throw new IllegalStateException("Identity key rotation requires PROTECTED mode");
        }
    }

    private record LegacyIdentity(UUID id, String document, String phone) {}

    record Coverage(String status, String credentialStatus, boolean deleted, long rows) {}
}
