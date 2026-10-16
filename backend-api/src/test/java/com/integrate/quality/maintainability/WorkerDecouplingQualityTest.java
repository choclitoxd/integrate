package com.integrate.quality.maintainability;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.integrate.modules.booking.config.BookingProperties;
import com.integrate.modules.booking.service.BookingExpirationService;
import com.integrate.modules.booking.service.model.ExpiredBatch;
import com.integrate.modules.booking.worker.BookingExpirationWorker;
import com.integrate.quality.support.QualityIntegrationTest;
import com.integrate.quality.support.QualityMetrics;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * RNF-26 (Mantenibilidad · modularidad): los procesos en segundo plano corren desacoplados de la API REST. Se
 * verifica la estructura (reglas ArchUnit sobre el código de producción) y el comportamiento: un worker que falla en
 * cada ejecución no detiene su planificador ni degrada los endpoints síncronos.
 */
@Tag("rnf-26")
@Tag("iso-mantenibilidad")
@DisplayName("[RNF-26] Modularidad y desacoplamiento de workers asíncronos")
class WorkerDecouplingQualityTest extends QualityIntegrationTest {

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("com.integrate");

    @Test
    @DisplayName("[RNF-26] Reglas de arquitectura: workers aislados, apagables y sin dependencias inversas")
    void workersAreStructurallyIsolated() {
        Map<String, ArchRule> rules = Map.of(
                "@Scheduled solo en paquetes worker", methods().that().areAnnotatedWith(Scheduled.class)
                        .should().beDeclaredInClassesThat().resideInAPackage("..worker.."),
                "workers apagables por propiedad", com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes()
                        .that().resideInAPackage("..worker..").and().haveSimpleNameEndingWith("Worker")
                        .should().beAnnotatedWith(ConditionalOnProperty.class),
                "controladores no dependen de workers", noClasses().that().resideInAPackage("..controller..")
                        .should().dependOnClassesThat().resideInAPackage("..worker.."),
                "servicios no dependen de workers", noClasses().that().resideInAPackage("..service..")
                        .should().dependOnClassesThat().resideInAPackage("..worker.."));

        List<String> violations = new ArrayList<>();
        rules.forEach((name, rule) -> {
            var result = rule.evaluate(PRODUCTION);
            if (result.hasViolation()) {
                violations.add(name + ": " + result.getFailureReport().getDetails());
            }
        });
        long workers = PRODUCTION.stream().filter(c -> c.getPackageName().endsWith(".worker")).count();

        QualityMetrics.record("RNF-26", "Reglas de desacoplamiento de workers cumplidas",
                QualityMetrics.percent(rules.size() - violations.size(), rules.size()), "%", "= 100 %",
                violations.isEmpty(), rules.size() + " reglas ArchUnit sobre " + workers + " workers: " + rules.keySet());
        assertThat(workers).isGreaterThanOrEqualTo(3);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("[RNF-26] Un worker que falla en cada ejecución no interrumpe la API ni su planificador")
    void aFailingWorkerDoesNotDegradeTheApi() throws Exception {
        Scenario s = scenario(12);
        AtomicInteger executions = new AtomicInteger();
        BookingExpirationService broken = new BookingExpirationService() {
            @Override
            public ExpiredBatch expireBatch(int limit) {
                executions.incrementAndGet();
                throw new IllegalStateException("falla simulada de la base de datos del worker");
            }
        };
        BookingExpirationWorker worker = new BookingExpirationWorker(broken, new BookingProperties(
                Duration.ofMinutes(15), Duration.ofHours(24),
                new BookingProperties.Expiration(true, Duration.ofMillis(50), 100)));

        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("quality-worker-");
        scheduler.initialize();
        int ok = 0;
        int calls = 0;
        try {
            scheduler.scheduleWithFixedDelay(worker::run, Duration.ofMillis(50));
            long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
            while (System.nanoTime() < deadline) {
                calls++;
                ok += getJson(availabilityPath(s.experienceId()), null).andReturn()
                        .getResponse().getStatus() == 200 ? 1 : 0;
                Thread.sleep(40);
            }
        } finally {
            scheduler.shutdown();
        }

        QualityMetrics.record("RNF-26", "Disponibilidad de la API con un worker fallando",
                QualityMetrics.percent(ok, calls), "%", "= 100 %", ok == calls && executions.get() >= 3,
                calls + " llamadas a la API mientras el worker de expiración falló " + executions.get()
                        + " veces seguidas (el planificador siguió ejecutándolo)");
        assertThat(executions.get()).as("el planificador sigue ejecutando el worker tras cada falla")
                .isGreaterThanOrEqualTo(3);
        assertThat(ok).isEqualTo(calls);
    }
}
