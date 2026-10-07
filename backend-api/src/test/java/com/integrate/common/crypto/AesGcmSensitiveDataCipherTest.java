package com.integrate.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.integrate.support.TestKeys;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AesGcmSensitiveDataCipherTest {

    private static final String AAD = "catalog.host_profiles.victim_condition_enc:0f1e2d3c-4b5a-4968-8776-655443322110";

    private final String keyV1 = TestKeys.sensitiveDataKey();
    private final String keyV2 = TestKeys.sensitiveDataKey();
    private final AesGcmSensitiveDataCipher cipher =
            new AesGcmSensitiveDataCipher(new SensitiveDataProperties(Map.of((short) 1, keyV1, (short) 2, keyV2), (short) 2));

    @Test
    void roundTripsWithTheCiphertextLayoutOfTheDomain() {
        byte[] sealed = cipher.encrypt("RUV-2019-000123", AAD, cipher.currentKeyVersion());

        // common.ciphertext: IV (12) || texto cifrado || tag (16).
        assertThat(sealed).hasSize(12 + "RUV-2019-000123".length() + 16);
        assertThat(cipher.decrypt(sealed, AAD, (short) 2)).isEqualTo("RUV-2019-000123");
    }

    @Test
    void usesAFreshIvForEveryValue() {
        assertThat(cipher.encrypt("true", AAD, (short) 2)).isNotEqualTo(cipher.encrypt("true", AAD, (short) 2));
    }

    @Test
    void rejectsAnotherRowOrColumn() {
        byte[] sealed = cipher.encrypt("true", AAD, (short) 2);

        assertThatThrownBy(() -> cipher.decrypt(sealed, AAD.replace("0f1e", "aaaa"), (short) 2))
                .isInstanceOf(SensitiveDataException.class);
        assertThatThrownBy(() -> cipher.decrypt(sealed, AAD.replace("victim_condition", "victimization_type"), (short) 2))
                .isInstanceOf(SensitiveDataException.class);
    }

    @Test
    void rejectsATamperedCiphertext() {
        byte[] sealed = cipher.encrypt("DESPLAZAMIENTO_FORZADO", AAD, (short) 2);
        sealed[sealed.length - 1] ^= 0x01;

        assertThatThrownBy(() -> cipher.decrypt(sealed, AAD, (short) 2)).isInstanceOf(SensitiveDataException.class);
    }

    @Test
    void decryptsOlderRowsWithTheirOwnKeyVersion() {
        byte[] sealedV1 = cipher.encrypt("true", AAD, (short) 1);

        assertThat(cipher.decrypt(sealedV1, AAD, (short) 1)).isEqualTo("true");
        assertThatThrownBy(() -> cipher.decrypt(sealedV1, AAD, (short) 2)).isInstanceOf(SensitiveDataException.class);
        assertThatThrownBy(() -> cipher.decrypt(sealedV1, AAD, (short) 3))
                .isInstanceOf(SensitiveDataException.class)
                .hasMessageContaining("Unknown");
    }

    @Test
    void failsFastWithoutAValidCurrentKey() {
        assertThatThrownBy(() -> new AesGcmSensitiveDataCipher(new SensitiveDataProperties(Map.of(), (short) 1)))
                .isInstanceOf(SensitiveDataException.class)
                .hasMessageContaining("SENSITIVE_DATA_KEY_V1");
        assertThatThrownBy(() -> new AesGcmSensitiveDataCipher(
                new SensitiveDataProperties(Map.of((short) 1, "c2hvcnQ="), (short) 1)))
                .isInstanceOf(SensitiveDataException.class)
                .hasMessageContaining("32 bytes");
    }
}
