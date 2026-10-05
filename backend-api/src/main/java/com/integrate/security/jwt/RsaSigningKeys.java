package com.integrate.security.jwt;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

/**
 * Carga la llave de sesión desde {@code JWT_SIGNING_KEY} con el JCA del JDK (sin {@code bcpkix},
 * ADR-0002). Fail-fast: cualquier problema aborta el arranque con un mensaje que nunca incluye
 * material de la llave.
 */
final class RsaSigningKeys {

    static final int MIN_KEY_BITS = 2048;
    private static final String PEM_BEGIN = "-----BEGIN PRIVATE KEY-----";
    private static final String PEM_END = "-----END PRIVATE KEY-----";

    private RsaSigningKeys() {
    }

    record KeyPair(RSAPrivateCrtKey privateKey, RSAPublicKey publicKey) {

        @Override
        public String toString() {
            return "KeyPair[bits=" + publicKey.getModulus().bitLength() + "]";
        }
    }

    /**
     * @param base64Pem PKCS#8 PEM ({@code BEGIN PRIVATE KEY}) codificado en Base64; se acepta
     *                  también el PEM en claro para comodidad en pruebas
     */
    static KeyPair load(String base64Pem) {
        if (base64Pem == null || base64Pem.isBlank()) {
            throw new IllegalStateException("JWT_SIGNING_KEY is not set (ADR-0005 §2)");
        }
        String pem = base64Pem.contains(PEM_BEGIN) ? base64Pem : decode(base64Pem.strip(), "JWT_SIGNING_KEY");
        if (!pem.contains(PEM_BEGIN) || !pem.contains(PEM_END)) {
            throw new IllegalStateException(
                    "JWT_SIGNING_KEY must be a PKCS#8 PEM private key (-----BEGIN PRIVATE KEY-----), Base64 encoded");
        }
        String body = pem.substring(pem.indexOf(PEM_BEGIN) + PEM_BEGIN.length(), pem.indexOf(PEM_END))
                .replaceAll("\\s", "");
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            if (!(factory.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(body)))
                    instanceof RSAPrivateCrtKey privateKey)) {
                throw new IllegalStateException("JWT_SIGNING_KEY must be an RSA private key with CRT parameters");
            }
            BigInteger modulus = privateKey.getModulus();
            if (modulus.bitLength() < MIN_KEY_BITS) {
                throw new IllegalStateException("JWT_SIGNING_KEY must be at least " + MIN_KEY_BITS + " bits");
            }
            RSAPublicKey publicKey = (RSAPublicKey) factory.generatePublic(
                    new RSAPublicKeySpec(modulus, privateKey.getPublicExponent()));
            return new KeyPair(privateKey, publicKey);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // Sin la causa en el mensaje: algunos proveedores incluyen bytes de la entrada.
            throw new IllegalStateException("JWT_SIGNING_KEY could not be parsed as an RSA PKCS#8 private key");
        }
    }

    private static String decode(String value, String variable) {
        try {
            return new String(Base64.getDecoder().decode(value), StandardCharsets.US_ASCII);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(variable + " is not valid Base64");
        }
    }
}
