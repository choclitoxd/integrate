package com.integrate.modules.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.integrate.common.audit.SensitiveAccessRecorder;
import com.integrate.common.crypto.AesGcmSensitiveDataCipher;
import com.integrate.common.crypto.SensitiveDataProperties;
import com.integrate.modules.auth.repository.UserRepository;
import com.integrate.support.TestKeys;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Llaves de cifrado con varias versiones junto al índice ciego (ADR-0046). Antes, declarar
 * {@code SENSITIVE_DATA_KEY_V2} sin valor (preparación de una rotación) impedía arrancar: la
 * comprobación de independencia entre llaves decodificaba también la versión vacía.
 */
class IdentityProtectionKeysTest {

    private final String keyV1 = TestKeys.sensitiveDataKey();
    private final String keyV2 = TestKeys.sensitiveDataKey();
    private final String indexKey = TestKeys.sensitiveDataKey();

    @Test
    void startsWithTheNextEncryptionVersionDeclaredWithoutValue() {
        SensitiveDataProperties keys = new SensitiveDataProperties(Map.of((short) 1, keyV1, (short) 2, ""), (short) 1);
        assertThatCode(() -> protection(keys, indexKey)).doesNotThrowAnyException();
    }

    @Test
    void startsWithTwoEncryptionVersionsDuringARotation() {
        SensitiveDataProperties keys = new SensitiveDataProperties(Map.of((short) 1, keyV1, (short) 2, keyV2), (short) 2);
        assertThatCode(() -> protection(keys, indexKey)).doesNotThrowAnyException();
    }

    @Test
    void stillRejectsAnIndexKeyEqualToAnyEncryptionVersion() {
        SensitiveDataProperties keys = new SensitiveDataProperties(Map.of((short) 1, keyV1, (short) 2, keyV2), (short) 2);
        assertThatThrownBy(() -> protection(keys, keyV1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("independent from encryption keys");
    }

    private IdentityProtection protection(SensitiveDataProperties keys, String encodedIndexKey) {
        return new IdentityProtection(mock(UserRepository.class), mock(JdbcTemplate.class),
                new AesGcmSensitiveDataCipher(keys), keys, mock(SensitiveAccessRecorder.class),
                IdentityProtection.Mode.DUAL, (short) 1, encodedIndexKey, (short) 0, "");
    }
}
