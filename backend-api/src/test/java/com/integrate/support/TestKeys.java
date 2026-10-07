package com.integrate.support;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Llaves RSA efímeras para pruebas (ADR-0005 §2): se generan en memoria en cada ejecución,
 * nunca se escriben en disco ni en el repositorio (SEC-DIR-004).
 */
public final class TestKeys {

    private static final KeyPair SESSION = generate(2048);

    private TestKeys() {
    }

    /** Llave de sesión en el formato de {@code JWT_SIGNING_KEY}: PKCS#8 PEM codificado en Base64. */
    public static String sessionSigningKey() {
        return toBase64Pem(SESSION);
    }

    /** Llave AES-256 aleatoria en el formato de {@code SENSITIVE_DATA_KEY_V1} (ADR-0007 §4). */
    public static String sensitiveDataKey() {
        byte[] key = new byte[32];
        new java.security.SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    public static KeyPair generate(int bits) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(bits);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String toPem(KeyPair keyPair) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(keyPair.getPrivate().getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----\n";
    }

    public static String toBase64Pem(KeyPair keyPair) {
        return Base64.getEncoder().encodeToString(toPem(keyPair).getBytes(StandardCharsets.US_ASCII));
    }
}
