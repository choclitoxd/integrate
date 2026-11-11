package com.integrate.modules.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.integrate.common.audit.SensitiveAccessRecorder;
import com.integrate.common.crypto.AesGcmSensitiveDataCipher;
import com.integrate.common.crypto.SensitiveDataProperties;
import com.integrate.modules.auth.entity.DocType;
import com.integrate.modules.auth.entity.User;
import com.integrate.modules.auth.repository.UserRepository;
import com.integrate.support.TestKeys;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Rotación de la llave de cifrado de identidad (ADR-0046, operación): ensayo local con PostgreSQL
 * descartable. Antes de arrancar Spring se prepara la base como en el corte real (migración, filas en
 * modo DUAL y corte). El contexto arranca en {@code PROTECTED} con la versión 1 y la versión 2 declaradas;
 * la prueba rota todas las filas a la versión 2 y verifica que todas sigan legibles.
 */
@SpringBootTest
@ActiveProfiles("test")
class IdentityRotationPostgresTest {

    private static final Path INFRA = Path.of("..", "infra", "postgres");
    private static final String OWNER_PASSWORD = UUID.randomUUID().toString();
    private static final String API_PASSWORD = UUID.randomUUID().toString();
    private static final String KEY_V1 = TestKeys.sensitiveDataKey();
    private static final String KEY_V2 = TestKeys.sensitiveDataKey();
    private static final String INDEX_KEY = TestKeys.sensitiveDataKey();
    private static final String PHONE = "+573000000001";
    private static final int ROWS = 25;

    @SuppressWarnings("resource") // vive toda la JVM; Ryuk lo elimina al terminar
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16.6-alpine")
            .withEnv("INTEGRATE_OWNER_PASSWORD", OWNER_PASSWORD)
            .withEnv("INTEGRATE_API_PASSWORD", API_PASSWORD)
            .withEnv("INTEGRATE_REPORTING_PASSWORD", UUID.randomUUID().toString())
            .withCopyFileToContainer(MountableFile.forHostPath(INFRA.resolve("init/00_bootstrap.sql")),
                    "/docker-entrypoint-initdb.d/00_bootstrap.sql");

    static {
        POSTGRES.start();
        prepareCutDatabase();
    }

    @Autowired
    private IdentityMigration migration;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void rotatesEveryRowToTheNextEncryptionVersionAndKeepsItReadable() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth.users WHERE doc_key_version = 1", Long.class))
                .isEqualTo(ROWS);

        long rotated = 0;
        int batch;
        do {
            batch = migration.rotateBatch(10);
            rotated += batch;
        } while (batch > 0);
        assertThat(rotated).isGreaterThanOrEqualTo(ROWS);

        assertThat(migration.verifyRotation(10)).isEqualTo(
                jdbc.queryForObject("SELECT count(*) FROM auth.users", Long.class));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth.users WHERE doc_key_version <> 2 OR phone_key_version <> 2",
                Long.class)).isZero();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String url = "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getFirstMappedPort() + "/integrate";
        registry.add("DATABASE_URL", () -> url);
        registry.add("DATABASE_USERNAME", () -> "integrate_api");
        registry.add("DATABASE_PASSWORD", () -> API_PASSWORD);
        registry.add("FLYWAY_USER", () -> "integrate_owner");
        registry.add("FLYWAY_PASSWORD", () -> OWNER_PASSWORD);
        registry.add("JWT_SIGNING_KEY", TestKeys::sessionSigningKey);
        registry.add("JWT_SIGNING_KEY_ID", () -> "session-test");
        registry.add("SENSITIVE_DATA_KEY_V1", () -> KEY_V1);
        registry.add("SENSITIVE_DATA_KEY_V2", () -> KEY_V2);
        registry.add("SENSITIVE_DATA_KEY_CURRENT_VERSION", () -> "2");
        registry.add("integrate.identity.mode", () -> "PROTECTED");
        registry.add("integrate.identity.index-key", () -> INDEX_KEY);
        registry.add("AWS_S3_SENSITIVE_BUCKET_NAME", () -> "integrate-sensitive-test");
        registry.add("AWS_S3_BUCKET_NAME", () -> "integrate-vouchers-test");
        registry.add("PAYMENT_PROVIDER", () -> "MOCK");
        registry.add("PAYMENT_WEBHOOK_SECRET_MOCK", TestKeys::sensitiveDataKey);
        registry.add("WOMPI_PUBLIC_KEY", () -> "pub_test_" + UUID.randomUUID());
        registry.add("WOMPI_INTEGRITY_SECRET", () -> "test_integrity_" + UUID.randomUUID());
        registry.add("WOMPI_EVENTS_SECRET", () -> "test_events_" + UUID.randomUUID());
        registry.add("VOUCHER_SIGNING_KEY", () -> TestKeys.toBase64Pem(TestKeys.generate(2048)));
        registry.add("VOUCHER_SIGNING_KEY_ID", () -> "voucher-rotacion");
        registry.add("SMTP_HOST", () -> "127.0.0.1");
        registry.add("SMTP_PORT", () -> 2525);
        registry.add("MAIL_FROM", () -> "Integrate <no-reply@integrate.test>");
        registry.add("FRONTEND_BASE_URL", () -> "https://app.integrate.test");
    }

    /** Migra, siembra filas en DUAL con la versión 1 y aplica el corte real, antes de arrancar Spring. */
    private static void prepareCutDatabase() {
        try {
            String url = "jdbc:postgresql://%s:%d/integrate".formatted(POSTGRES.getHost(), POSTGRES.getFirstMappedPort());
            Flyway.configure().dataSource(url, "integrate_owner", OWNER_PASSWORD)
                    .defaultSchema("common").load().migrate();
            JdbcTemplate owner = new JdbcTemplate(new DriverManagerDataSource(url, "integrate_owner", OWNER_PASSWORD));
            JdbcTemplate api = new JdbcTemplate(new DriverManagerDataSource(url, "integrate_api", API_PASSWORD));

            SensitiveDataProperties keys = new SensitiveDataProperties(Map.of((short) 1, KEY_V1), (short) 1);
            IdentityProtection dual = new IdentityProtection(mock(UserRepository.class), api,
                    new AesGcmSensitiveDataCipher(keys), keys, mock(SensitiveAccessRecorder.class),
                    IdentityProtection.Mode.DUAL, (short) 1, INDEX_KEY, (short) 0, "");
            for (int i = 0; i < ROWS; i++) {
                dual.insertIdentity(user("Rotacion" + String.format("%04d", i)));
            }
            owner.execute(Files.readString(INFRA.resolve("operations/adr0046-cut-legacy-user-identity.sql")));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo preparar la base del ensayo de rotación", e);
        }
    }

    private static User user(String document) {
        return User.register("rotacion-" + UUID.randomUUID() + "@example.test", "$argon2id$integration",
                "Synthetic Test", DocType.PA, document, PHONE);
    }
}
