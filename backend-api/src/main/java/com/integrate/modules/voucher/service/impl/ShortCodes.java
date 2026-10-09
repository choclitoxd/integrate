package com.integrate.modules.voucher.service.impl;

import java.security.SecureRandom;

/**
 * Código corto de contingencia (ADR-0010 §4): 8 caracteres del alfabeto de {@code ck_vouchers_short_code}
 * ({@code A-HJ-NP-Z2-9}, sin I, O, 0 ni 1), 32 símbolos y 40 bits de entropía.
 */
final class ShortCodes {

    static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private ShortCodes() {
    }

    static String next() {
        char[] code = new char[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            code[i] = ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length()));
        }
        return new String(code);
    }
}
