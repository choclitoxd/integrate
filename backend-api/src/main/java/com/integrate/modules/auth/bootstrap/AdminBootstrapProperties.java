package com.integrate.modules.auth.bootstrap;

import com.integrate.modules.auth.entity.DocType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Primer administrador (ADR-0005 §9). Se enlaza desde las variables {@code BOOTSTRAP_ADMIN_*}
 * en {@code application.yml}; sin correo y contraseña el arranque no crea nada. SEC-DIR-004: la
 * contraseña solo vive en el {@code .env} local o en Secrets Manager y se retira tras el primer arranque.
 */
@ConfigurationProperties("integrate.bootstrap.admin")
public record AdminBootstrapProperties(
        String email,
        String password,
        @DefaultValue("Administrador de plataforma") String fullName,
        @DefaultValue("PA") DocType docType,
        @DefaultValue("ADMIN0001") String docNumber,
        @DefaultValue("+10000000000") String phone) {

    public boolean isConfigured() {
        return email != null && !email.isBlank() && password != null && !password.isBlank();
    }

    @Override
    public String toString() {
        return "AdminBootstrapProperties[email=" + email + "]";
    }
}
