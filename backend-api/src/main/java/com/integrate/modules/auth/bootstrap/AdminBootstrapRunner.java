package com.integrate.modules.auth.bootstrap;

import com.integrate.common.exception.BusinessRuleException;
import com.integrate.modules.auth.service.RegistrationService;
import com.integrate.modules.auth.service.RegistrationService.AdminBootstrapCommand;
import com.integrate.modules.auth.service.RegistrationService.BootstrapOutcome;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Crea el primer {@code ADMIN} al arrancar si se definieron {@code BOOTSTRAP_ADMIN_EMAIL} y
 * {@code BOOTSTRAP_ADMIN_PASSWORD} (ADR-0005 §9). Idempotente. Una configuración inválida
 * aborta el arranque (fail-fast) en lugar de dejar la plataforma sin administrador en silencio.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {

    private final AdminBootstrapProperties properties;
    private final RegistrationService registrationService;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isConfigured()) {
            log.debug("Admin bootstrap disabled (BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD not set)");
            return;
        }
        BootstrapOutcome outcome;
        try {
            outcome = registrationService.bootstrapAdmin(new AdminBootstrapCommand(properties.email(),
                    properties.password(), properties.fullName(), properties.docType(), properties.docNumber(),
                    properties.phone()));
        } catch (BusinessRuleException e) {
            throw new IllegalStateException("Invalid BOOTSTRAP_ADMIN_* configuration: " + e.getExtensions(), e);
        }
        switch (outcome) {
            // Sin el correo: los logs no llevan datos personales (R-05).
            case CREATED -> log.warn("Bootstrap ADMIN account created. Remove BOOTSTRAP_ADMIN_* from the "
                    + "environment now.");
            case ADMIN_ALREADY_EXISTS -> log.info("Admin bootstrap skipped: an active ADMIN already exists. "
                    + "BOOTSTRAP_ADMIN_* can be removed from the environment.");
            case IDENTITY_TAKEN -> log.warn("Admin bootstrap skipped: the email or document of BOOTSTRAP_ADMIN_* "
                    + "already belongs to another account; it is not elevated automatically.");
        }
    }
}
