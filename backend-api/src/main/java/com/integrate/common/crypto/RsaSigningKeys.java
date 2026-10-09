package com.integrate.common.crypto;

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
 * Carga una llave privada RSA de firma (sesión, ADR-0005; vouchers, ADR-0010) con el JCA del JDK, sin
 * {@code bcpkix} (ADR-0002), y deriva su llave pública. Fail-fast: cualquier problema aborta el arranque con un
 * mensaje que nombra la variable y nunca incluye material de la llave.
 */
public final class RsaSigningKeys {

    public static final int MIN_KEY_BITS = 2048;
    private static final String PEM_BEGIN = "-----BEGIN PRIVATE KEY-----";
    private static final String PEM_END = "-----END PRIVATE KEY-----";

    private RsaSigningKeys() {
    }

    public record KeyPair(RSAPrivateCrtKey privateKey, RSAPublicKey publicKey) {

        @Override
        public String toString() {
            return "KeyPair[bits=" + publicKey.getModulus().bitLength() + "]";
        }
    }

    /**
     * @param base64Pem PKCS#8 PEM ({@code BEGIN PRIVATE KEY}) codificado en Base64; se acepta
     *                  también el PEM en claro para comodidad en pruebas
     * @param variable  nombre de la variable de entorno, solo para los mensajes de error
     */
    public static KeyPair load(String base64Pem, String variable) {
        if (base64Pem == null || base64Pem.isBlank()) {
            throw new IllegalStateException(variable + " is not set");
        }
        String pem = base64Pem.contains(PEM_BEGIN) ? base64Pem : decode(base64Pem.strip(), variable);
        if (!pem.contains(PEM_BEGIN) || !pem.contains(PEM_END)) {
            throw new IllegalStateException(
                    variable + " must be a PKCS#8 PEM private key (-----BEGIN PRIVATE KEY-----), Base64 encoded");
        }
        String body = pem.substring(pem.indexOf(PEM_BEGIN) + PEM_BEGIN.length(), pem.indexOf(PEM_END))
                .replaceAll("\\s", "");
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            if (!(factory.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(body)))
                    instanceof RSAPrivateCrtKey privateKey)) {
                throw new IllegalStateException(variable + " must be an RSA private key with CRT parameters");
            }
            BigInteger modulus = privateKey.getModulus();
            if (modulus.bitLength() < MIN_KEY_BITS) {
                throw new IllegalStateException(variable + " must be at least " + MIN_KEY_BITS + " bits");
            }
            RSAPublicKey publicKey = (RSAPublicKey) factory.generatePublic(
                    new RSAPublicKeySpec(modulus, privateKey.getPublicExponent()));
            return new KeyPair(privateKey, publicKey);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // Sin la causa en el mensaje: algunos proveedores incluyen bytes de la entrada.
            throw new IllegalStateException(variable + " could not be parsed as an RSA PKCS#8 private key");
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
