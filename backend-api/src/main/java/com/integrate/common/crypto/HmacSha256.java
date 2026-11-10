package com.integrate.common.crypto;

import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 en hexadecimal minúscula, para firmas de webhooks (ADR-0009 §4). Vive aquí porque {@code javax.crypto}
 * tiene un único punto de entrada auditable (ADR-0007 §4).
 */
public final class HmacSha256 {

    private HmacSha256() {
    }

    /** HMAC-SHA256 en bytes, para índices ciegos (ADR-0046 §2). La clave nunca se devuelve ni se registra. */
    public static byte[] mac(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(message);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is mandatory in every JRE", e);
        }
    }

    public static String hex(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(message));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is mandatory in every JRE", e);
        }
    }
}
