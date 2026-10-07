package com.integrate.modules.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ActivationCodesTest {

    @Test
    void newCodesAreGroupedAndCanonicalizable() {
        String code = ActivationCodes.newCode();

        assertThat(code).matches("^[0-9A-HJKMNP-TV-Z]{5}-[0-9A-HJKMNP-TV-Z]{5}$");
        assertThat(ActivationCodes.normalize(code)).isEqualTo(code.replace("-", ""));
    }

    @Test
    void normalizationToleratesDictationMistakes() {
        assertThat(ActivationCodes.normalize("ab1o2-il3xz")).isEqualTo("AB102113XZ");
        assertThat(ActivationCodes.normalize(" AB102 113XZ ")).isEqualTo("AB102113XZ");
    }

    @Test
    void rejectsWhatCannotBeACode() {
        assertThat(ActivationCodes.normalize(null)).isNull();
        assertThat(ActivationCodes.normalize("AB102-113X")).isNull();
        assertThat(ActivationCodes.normalize("AB102-113XU")).isNull();
        assertThat(ActivationCodes.normalize("AB102-113X!")).isNull();
    }
}
