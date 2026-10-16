package com.integrate.quality.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.integrate.modules.auth.entity.DocType;
import com.integrate.modules.auth.service.AccessTokenIssuer.IssuedToken;
import com.integrate.modules.auth.service.CredentialActivationService.IssuedActivationCode;
import com.integrate.modules.auth.service.RegistrationService.AdminBootstrapCommand;
import com.integrate.quality.support.QualityMetrics;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * RNF-04 y RNF-01 (Seguridad · confidencialidad de credenciales): los objetos que transportan un secreto (token de
 * acceso, código de activación, contraseña del administrador inicial) no lo exponen al imprimirse en un log o en un
 * mensaje de excepción.
 */
@Tag("quality")
@Tag("rnf-04")
@Tag("iso-seguridad")
@DisplayName("[RNF-04] Los secretos en tránsito interno no se filtran a los logs")
class SecretRedactionQualityTest {

    @Test
    @DisplayName("[RNF-04] 100 % de los portadores de secretos ocultan el secreto en toString()")
    void secretCarriersRedactTheSecret() {
        String token = "eyJ" + UUID.randomUUID();
        String code = "ABCDE-" + UUID.randomUUID().toString().substring(0, 5).toUpperCase();
        String password = "clave-" + UUID.randomUUID();
        List<String[]> carriers = List.of(
                new String[] {new IssuedToken(token, 900).toString(), token},
                new String[] {new IssuedActivationCode(code, Instant.EPOCH).toString(), code},
                new String[] {new AdminBootstrapCommand("admin@ejemplo.com", password, "Administrador",
                        DocType.PA, "P1234567", "+10000000000").toString(), password});

        long redacted = carriers.stream().filter(c -> !c[0].contains(c[1])).count();
        QualityMetrics.record("RNF-04", "Portadores de secretos que ocultan el secreto al imprimirse",
                QualityMetrics.percent(redacted, carriers.size()), "%", "= 100 %", redacted == carriers.size(),
                "IssuedToken, IssuedActivationCode y AdminBootstrapCommand");
        assertThat(redacted).isEqualTo(carriers.size());
    }
}
