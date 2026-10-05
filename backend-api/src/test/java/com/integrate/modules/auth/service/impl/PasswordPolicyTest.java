package com.integrate.modules.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.integrate.common.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

/** Política de contraseña NIST SP 800-63B (ADR-0005 §3). */
class PasswordPolicyTest {

    @Test
    void acceptsTwelveToOneHundredTwentyEightCharactersWithoutCompositionRules() {
        assertThatCode(() -> PasswordPolicy.check("solo minusculas", "password")).doesNotThrowAnyException();
        assertThatCode(() -> PasswordPolicy.check("a".repeat(128), "password")).doesNotThrowAnyException();
    }

    @Test
    void countsCodePointsNotUtf16Units() {
        // 12 emojis = 24 unidades UTF-16 pero 12 caracteres.
        assertThatCode(() -> PasswordPolicy.check("😀".repeat(12), "password")).doesNotThrowAnyException();
        assertThatThrownBy(() -> PasswordPolicy.check("😀".repeat(11), "password"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsOutOfRangeNullAndNullCharacters() {
        assertThatThrownBy(() -> PasswordPolicy.check("a".repeat(11), "password")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> PasswordPolicy.check("a".repeat(129), "password")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> PasswordPolicy.check(null, "password")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> PasswordPolicy.check("clave\0con-nulo", "password"))
                .isInstanceOf(BusinessRuleException.class);
    }
}
