package com.integrate.modules.auth.service.impl;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * Códigos de activación de 10 caracteres en base32 de Crockford (50 bits), mostrados como
 * {@code XXXXX-XXXXX}. La normalización tolera guiones, espacios, minúsculas y las confusiones
 * típicas al dictar ({@code O→0}, {@code I/L→1}).
 */
final class ActivationCodes {

    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int LENGTH = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private ActivationCodes() {
    }

    static String newCode() {
        StringBuilder code = new StringBuilder(LENGTH + 1);
        for (int i = 0; i < LENGTH; i++) {
            if (i == LENGTH / 2) {
                code.append('-');
            }
            code.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return code.toString();
    }

    /** Forma canónica que se hashea; {@code null} si no puede ser un código válido. */
    static String normalize(String code) {
        if (code == null) {
            return null;
        }
        StringBuilder canonical = new StringBuilder(LENGTH);
        for (char c : code.toUpperCase(Locale.ROOT).toCharArray()) {
            switch (c) {
                case '-', ' ' -> { }
                case 'O' -> canonical.append('0');
                case 'I', 'L' -> canonical.append('1');
                default -> {
                    if (new String(ALPHABET).indexOf(c) < 0) {
                        return null;
                    }
                    canonical.append(c);
                }
            }
        }
        return canonical.length() == LENGTH ? canonical.toString() : null;
    }
}
