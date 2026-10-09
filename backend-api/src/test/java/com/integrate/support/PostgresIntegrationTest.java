package com.integrate.support;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Base de pruebas de integración contra PostgreSQL 16 real (Testcontainers). El contenedor
 * ejecuta el mismo {@code infra/postgres/init/00_bootstrap.sql} que el entorno local, de modo
 * que la aplicación corre como {@code integrate_api} (solo DML) y Flyway como {@code integrate_owner}:
 * los privilegios de V9 y los triggers append-only se ejercitan de verdad.
 *
 * <p>Un único contenedor por JVM (patrón singleton); las contraseñas de los roles son aleatorias
 * por ejecución. Requiere Docker. S3 se sustituye por {@link InMemoryDocumentStorage}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresIntegrationTest.TestClockConfig.class)
public abstract class PostgresIntegrationTest {

    protected static final Instant START = Instant.parse("2026-10-05T12:00:00Z");

    private static final String OWNER_PASSWORD = UUID.randomUUID().toString();
    private static final String API_PASSWORD = UUID.randomUUID().toString();
    private static final String SENSITIVE_DATA_KEY = TestKeys.sensitiveDataKey();

    /** Secretos de pasarela generados en memoria por ejecución, para firmar webhooks en las pruebas (ADR-0009 §9). */
    protected static final String MOCK_WEBHOOK_SECRET = TestKeys.sensitiveDataKey();
    protected static final String WOMPI_EVENTS_SECRET = "test_events_" + UUID.randomUUID();
    protected static final String WOMPI_INTEGRITY_SECRET = "test_integrity_" + UUID.randomUUID();
    protected static final String WOMPI_PUBLIC_KEY = "pub_test_" + UUID.randomUUID();

    @SuppressWarnings("resource") // vive toda la JVM; Ryuk lo elimina al terminar
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16.6-alpine")
            .withEnv("INTEGRATE_OWNER_PASSWORD", OWNER_PASSWORD)
            .withEnv("INTEGRATE_API_PASSWORD", API_PASSWORD)
            .withEnv("INTEGRATE_REPORTING_PASSWORD", UUID.randomUUID().toString())
            .withCopyFileToContainer(
                    MountableFile.forHostPath(Path.of("..", "infra", "postgres", "init", "00_bootstrap.sql")),
                    "/docker-entrypoint-initdb.d/00_bootstrap.sql");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected InMemoryDocumentStorage documentStorage;

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
        registry.add("SENSITIVE_DATA_KEY_V1", () -> SENSITIVE_DATA_KEY);
        registry.add("AWS_S3_SENSITIVE_BUCKET_NAME", () -> "integrate-sensitive-test");
        registry.add("PAYMENT_PROVIDER", () -> "MOCK");
        registry.add("PAYMENT_WEBHOOK_SECRET_MOCK", () -> MOCK_WEBHOOK_SECRET);
        registry.add("WOMPI_PUBLIC_KEY", () -> WOMPI_PUBLIC_KEY);
        registry.add("WOMPI_INTEGRITY_SECRET", () -> WOMPI_INTEGRITY_SECRET);
        registry.add("WOMPI_EVENTS_SECRET", () -> WOMPI_EVENTS_SECRET);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClockConfig {

        /** Reemplaza al {@link Clock} de {@code TimeConfig} en todo el contexto de pruebas. */
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(START);
        }

        /** Sustituye a S3: las pruebas no dependen de LocalStack (ADR-0007 §8). */
        @Bean
        @Primary
        InMemoryDocumentStorage inMemoryDocumentStorage(MutableClock clock) {
            return new InMemoryDocumentStorage(clock);
        }
    }
}
