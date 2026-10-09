package com.integrate.modules.payment.gateway;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/** Hashes y comparación en tiempo constante para las firmas de las pasarelas. */
final class Digests {

    private Digests() {
    }

    static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 is mandatory in every JRE", e);
        }
    }

    /** Compara dos hexadecimales sin distinguir mayúsculas y en tiempo constante ({@link MessageDigest#isEqual}). */
    static boolean hexEquals(String expected, String received) {
        if (received == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII),
                received.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
    }
}
