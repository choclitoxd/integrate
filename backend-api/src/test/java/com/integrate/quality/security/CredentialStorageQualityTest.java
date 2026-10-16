package com.integrate.quality.security;

import static com.integrate.modules.auth.AuthTestData.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;

import com.integrate.quality.support.QualityIntegrationTest;
import com.integrate.quality.support.QualityMetrics;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * RNF-04 (Seguridad · hashing de credenciales). El plan pide BCrypt; el sistema usa Argon2id (ADR-0005 §3), que el
 * plan acepta como "algoritmo robusto con salting" en su §1.1. La prueba exige hash robusto con sal en el 100 % de
 * las credenciales y reporta aparte qué porción es Argon2id.
 */
@Tag("rnf-04")
@Tag("iso-seguridad")
@DisplayName("[RNF-04] Hashing y custodia criptográfica de credenciales")
class CredentialStorageQualityTest extends QualityIntegrationTest {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("[RNF-04] 100 % de contraseñas con hash robusto y sal, ninguna en claro")
    void everyStoredPasswordIsASaltedRobustHash() {
        // Dos usuarios con la misma contraseña: la sal debe producir hashes distintos.
        String first = registerTourist();
        String second = registerTourist();
        String firstHash = hashOf(first);
        String secondHash = hashOf(second);
        assertThat(firstHash).isNotEqualTo(secondHash);
        assertThat(passwordEncoder.matches(PASSWORD, firstHash)).isTrue();

        List<String> hashes = jdbc.queryForList(
                "SELECT password_hash FROM auth.users WHERE status <> 'ANONYMIZED'", String.class);
        long robust = hashes.stream().filter(h -> h.startsWith("$argon2id$") || h.matches("^\\$2[aby]\\$1[2-9]\\$.*"))
                .count();
        long argon2 = hashes.stream().filter(h -> h.startsWith("$argon2id$")).count();
        long plaintext = hashes.stream().filter(h -> h.contains(PASSWORD)).count();

        QualityMetrics.record("RNF-04", "Contrasenas almacenadas con hash robusto y sal",
                QualityMetrics.percent(robust, hashes.size()), "%", "= 100 %", robust == hashes.size() && plaintext == 0,
                hashes.size() + " credenciales; Argon2id = " + argon2 + "; texto plano = " + plaintext);
        QualityMetrics.record("RNF-04", "Credenciales con Argon2id",
                QualityMetrics.percent(argon2, hashes.size()), "%", "informativo (el plan nombra BCrypt)", true,
                "Argon2id m=19 MiB, t=2 (ADR-0005 §3)");
        assertThat(plaintext).isZero();
        assertThat(robust).isEqualTo(hashes.size());
    }

    private String hashOf(String email) {
        return jdbc.queryForObject("SELECT password_hash FROM auth.users WHERE email = ?", String.class, email);
    }
}
