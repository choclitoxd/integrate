package com.integrate.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.integrate.support.TestKeys;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** Carga fail-fast de {@code JWT_SIGNING_KEY} (ADR-0005 §2). */
class RsaSigningKeysTest {

    @Test
    void loadsBase64Pkcs8PemAndDerivesThePublicKey() {
        KeyPair pair = TestKeys.generate(2048);

        RsaSigningKeys.KeyPair loaded = RsaSigningKeys.load(TestKeys.toBase64Pem(pair), "JWT_SIGNING_KEY");

        assertThat(loaded.publicKey()).isEqualTo(pair.getPublic());
        assertThat(loaded.privateKey().getEncoded()).isEqualTo(pair.getPrivate().getEncoded());
        assertThat(loaded.toString()).doesNotContain(loaded.privateKey().getModulus().toString());
    }

    @Test
    void acceptsPlainPem() {
        KeyPair pair = TestKeys.generate(2048);

        assertThat(RsaSigningKeys.load(TestKeys.toPem(pair), "JWT_SIGNING_KEY").publicKey()).isEqualTo(pair.getPublic());
    }

    @Test
    void rejectsMissingKey() {
        assertThatThrownBy(() -> RsaSigningKeys.load(" ", "JWT_SIGNING_KEY")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SIGNING_KEY is not set");
    }

    @Test
    void rejectsKeysShorterThan2048Bits() {
        String weak = TestKeys.toBase64Pem(TestKeys.generate(1024));

        assertThatThrownBy(() -> RsaSigningKeys.load(weak, "JWT_SIGNING_KEY")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 2048 bits");
    }

    @Test
    void rejectsGarbageWithoutEchoingIt() {
        String garbage = Base64.getEncoder().encodeToString(
                "-----BEGIN PRIVATE KEY-----\nc2VjcmV0bw==\n-----END PRIVATE KEY-----".getBytes(StandardCharsets.US_ASCII));

        assertThatThrownBy(() -> RsaSigningKeys.load(garbage, "JWT_SIGNING_KEY")).isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("c2VjcmV0bw")
                .hasNoCause();
        assertThatThrownBy(() -> RsaSigningKeys.load("%%% no es base64 %%%", "JWT_SIGNING_KEY")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not valid Base64");
    }
}
