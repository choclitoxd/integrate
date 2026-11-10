package com.integrate.modules.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.integrate.common.audit.SensitiveAccessRecorder;
import com.integrate.common.crypto.AesGcmSensitiveDataCipher;
import com.integrate.common.crypto.SensitiveDataProperties;
import com.integrate.common.exception.BusinessRuleException;
import com.integrate.common.exception.ErrorCode;
import com.integrate.modules.auth.entity.DocType;
import com.integrate.modules.auth.entity.User;
import com.integrate.modules.auth.repository.UserRepository;
import com.integrate.support.TestKeys;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

class IdentityCutPostgresTest {
    private static final Path INFRA = Path.of("..", "infra", "postgres");
    private static final String PHONE = "+573000000001";
    private final SensitiveDataProperties encryptionKeys =
            new SensitiveDataProperties(Map.of((short) 1, TestKeys.sensitiveDataKey()), (short) 1);
    private final AesGcmSensitiveDataCipher cipher = new AesGcmSensitiveDataCipher(encryptionKeys);

    @Test
    void gatesCutAndIndexPromotionAndEnforcesProtectedUniqueness() throws Exception {
        String ownerPassword = UUID.randomUUID().toString();
        String apiPassword = UUID.randomUUID().toString();
        try (PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.6-alpine")
                .withEnv("INTEGRATE_OWNER_PASSWORD", ownerPassword)
                .withEnv("INTEGRATE_API_PASSWORD", apiPassword)
                .withEnv("INTEGRATE_REPORTING_PASSWORD", UUID.randomUUID().toString())
                .withCopyFileToContainer(MountableFile.forHostPath(INFRA.resolve("init/00_bootstrap.sql")),
                        "/docker-entrypoint-initdb.d/00_bootstrap.sql")) {
            postgres.start();
            String url = "jdbc:postgresql://%s:%d/integrate".formatted(postgres.getHost(), postgres.getFirstMappedPort());
            Flyway.configure().dataSource(url, "integrate_owner", ownerPassword)
                    .defaultSchema("common").load().migrate();
            JdbcTemplate owner = new JdbcTemplate(new DriverManagerDataSource(url, "integrate_owner", ownerPassword));
            JdbcTemplate api = new JdbcTemplate(new DriverManagerDataSource(url, "integrate_api", apiPassword));
            String cut = Files.readString(INFRA.resolve("operations/adr0046-cut-legacy-user-identity.sql"));
            String promote = Files.readString(INFRA.resolve("operations/adr0046-promote-next-user-index.sql"));

            assertThat(owner.queryForObject("""
                    SELECT has_column_privilege('integrate_readonly', 'auth.users', 'doc_number', 'SELECT')
                        OR has_column_privilege('integrate_readonly', 'auth.users', 'phone', 'SELECT')
                        OR has_column_privilege('integrate_readonly', 'auth.users', 'document_index', 'SELECT')
                    """, Boolean.class)).isFalse();

            String activeKey = TestKeys.sensitiveDataKey();
            String nextKey = TestKeys.sensitiveDataKey();
            IdentityProtection legacy = protection(api, IdentityProtection.Mode.LEGACY, (short) 1, "", (short) 0, "");
            User uncovered = user("Uncovered01");
            legacy.insertIdentity(uncovered);
            assertThatThrownBy(() -> owner.execute(cut)).isInstanceOf(DataAccessException.class)
                    .rootCause().hasMessageContaining("Identity cut rejected");
            owner.update("DELETE FROM auth.users WHERE id = ?", uncovered.getId());

            IdentityProtection dual = protection(api, IdentityProtection.Mode.DUAL, (short) 1, activeKey, (short) 0, "");
            User existing = user("Existing01");
            dual.insertIdentity(existing);
            owner.execute(cut);
            assertThatThrownBy(legacy::verifyStorageMode).hasMessageContaining("cutover state");
            assertThat(owner.queryForObject("""
                    SELECT count(*) FROM information_schema.columns
                    WHERE table_schema = 'auth' AND table_name = 'users' AND column_name IN ('doc_number', 'phone')
                    """, Integer.class)).isZero();

            IdentityProtection active =
                    protection(api, IdentityProtection.Mode.PROTECTED, (short) 1, activeKey, (short) 0, "");
            active.verifyStorageMode();
            assertConcurrentDuplicateConflict(active);

            assertThatThrownBy(() -> owner.execute(promote)).isInstanceOf(DataAccessException.class);
            IdentityProtection rotating =
                    protection(api, IdentityProtection.Mode.PROTECTED, (short) 1, activeKey, (short) 2, nextKey);
            owner.update("""
                    UPDATE auth.users SET document_index_next = ?, document_index_next_version = 2 WHERE id = ?
                    """, rotating.nextIndex(DocType.PA, existing.getDocNumber()), existing.getId());
            owner.update("""
                    UPDATE auth.users SET document_index_next = ?, document_index_next_version = 2
                    WHERE document_index = ?
                    """, rotating.nextIndex(DocType.PA, "Race01"), rotating.index(DocType.PA, "Race01"));
            owner.execute(promote);

            assertThatThrownBy(active::verifyStorageMode).hasMessageContaining("active database index");
            IdentityProtection promoted =
                    protection(api, IdentityProtection.Mode.PROTECTED, (short) 2, nextKey, (short) 0, "");
            promoted.verifyStorageMode();
            assertThat(owner.queryForObject("SELECT document_index_version FROM auth.users WHERE id = ?",
                    Short.class, existing.getId())).isEqualTo((short) 2);
            assertThat(insert(promoted, existing.getDocNumber())).isFalse();
            assertThat(insert(promoted, "AfterCut01")).isTrue();
        }
    }

    private void assertConcurrentDuplicateConflict(IdentityProtection active) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> {
                start.await();
                return insert(active, "Race01");
            });
            var second = workers.submit(() -> {
                start.await();
                return insert(active, "Race01");
            });
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isNotEqualTo(second.get(10, TimeUnit.SECONDS));
        }
    }

    private IdentityProtection protection(JdbcTemplate jdbc, IdentityProtection.Mode mode, short version,
                                          String key, short nextVersion, String nextKey) {
        return new IdentityProtection(mock(UserRepository.class), jdbc, cipher, encryptionKeys,
                mock(SensitiveAccessRecorder.class), mode, version, key, nextVersion, nextKey);
    }

    private boolean insert(IdentityProtection protection, String document) {
        try {
            protection.insertIdentity(user(document));
            return true;
        } catch (BusinessRuleException error) {
            assertThat(error.getErrorCode()).isEqualTo(ErrorCode.AUTH_USER_ALREADY_EXISTS);
            return false;
        }
    }

    private User user(String document) {
        return User.register("identity-" + UUID.randomUUID() + "@example.test", "$argon2id$integration", "Synthetic Test",
                DocType.PA, document, PHONE);
    }
}
