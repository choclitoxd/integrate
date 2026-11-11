package com.integrate.modules.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.integrate.common.exception.BusinessRuleException;
import com.integrate.common.exception.ErrorCode;
import com.integrate.modules.auth.entity.DocType;
import com.integrate.modules.auth.entity.User;
import com.integrate.modules.auth.repository.UserRepository;
import com.integrate.support.PostgresIntegrationTest;
import com.integrate.support.TestKeys;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class IdentityProtectionIntegrationTest extends PostgresIntegrationTest {
    private static final String INDEX_KEY = TestKeys.sensitiveDataKey();
    private static final String PHONE = "+573000000001";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;
    @Autowired private EntityManager entityManager;
    @Autowired private IdentityProtection protection;
    @Autowired private IdentityMigration migration;

    @DynamicPropertySource
    static void identityProperties(DynamicPropertyRegistry registry) {
        registry.add("integrate.identity.mode", () -> "DUAL");
        registry.add("integrate.identity.index-key", () -> INDEX_KEY);
    }

    @Test
    void writesBothRepresentationsAndPreservesDocumentScopeAndCase() {
        String document = "Ab" + UUID.randomUUID().toString().substring(0, 8);
        User account = register(DocType.PA, document);
        register(DocType.CC, document);
        register(DocType.PA, document.toUpperCase(java.util.Locale.ROOT));

        User persisted = protection.findByDocument(DocType.PA, document).orElseThrow();
        assertThat(persisted.getId()).isEqualTo(account.getId());
        assertThat(persisted.getEncryptedDocument()).isNotEqualTo(document.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(protection.readDocument(persisted)).isEqualTo(document);
        assertThat(protection.readPhone(persisted)).isEqualTo(PHONE);
        assertThat(jdbc.queryForObject("SELECT doc_number FROM auth.users WHERE id = ?", String.class, account.getId()))
                .isEqualTo(document);
        assertThatThrownBy(() -> register(DocType.PA, document)).isInstanceOfSatisfying(
                BusinessRuleException.class, error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.AUTH_USER_ALREADY_EXISTS));
    }

    @Test
    void backfillsResumablyAndInvalidatesAnOldWritersStaleCiphertext() {
        UUID id = UUID.randomUUID();
        String document = "LG" + id.toString().substring(0, 8);
        jdbc.update("""
                INSERT INTO auth.users(id, email, password_hash, full_name, doc_type, doc_number, phone)
                VALUES (?, ?, '$argon2id$integration', 'Legacy identity', 'PA', ?, ?)
                """, id, email(), document, PHONE);
        assertThat(protection.readPhone(users.findById(id).orElseThrow())).isEqualTo(PHONE);
        entityManager.clear();
        assertThat(migration.missing()).isPositive();
        drainBackfill();

        User account = users.findByIdForUpdate(id).orElseThrow();
        assertThat(account.getEncryptedPhone()).isNotNull();
        protection.changePhone(account, "+573000000002");
        users.flush();
        jdbc.update("UPDATE auth.users SET phone = '+573000000003' WHERE id = ?", id);
        entityManager.clear();

        assertThat(users.findById(id).orElseThrow().getEncryptedPhone()).isNull();
        entityManager.clear();
        assertThatThrownBy(() -> migration.verify(100)).hasMessage("Protected identity verification failed");
    }

    @Test
    void anonymizationReplacesOriginalLookupAndEncryptsPlaceholders() {
        User account = register(DocType.PA, "AN" + UUID.randomUUID().toString().substring(0, 8));
        migration.anonymize(account.getId());
        entityManager.clear();

        assertThat(protection.findByDocument(DocType.PA, account.getDocNumber())).isEmpty();
        User anonymized = users.findById(account.getId()).orElseThrow();
        assertThat(anonymized.isClosed()).isTrue();
        assertThat(anonymized.getEncryptedPhone()).isNotNull();
        assertThat(protection.readPhone(anonymized)).isEqualTo("+10000000000");
    }

    private void drainBackfill() {
        while (migration.backfillBatch(10) != 0) {
            // Each invocation represents a batch. Running again must be a no-op.
        }
        assertThat(migration.missing()).isZero();
        assertThat(migration.verify(10)).isNotEmpty();
    }

    private User register(DocType type, String document) {
        User user = User.register(email(), "$argon2id$integration", "Synthetic Test", type, document, PHONE);
        protection.insertIdentity(user);
        return user;
    }

    private static String email() {
        return "identity-" + UUID.randomUUID() + "@example.test";
    }
}
