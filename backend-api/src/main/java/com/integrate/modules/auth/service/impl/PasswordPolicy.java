package com.integrate.modules.auth.service.impl;

/**
 * Política de contraseña (NIST SP 800-63B, ADR-0005 §3): 12 a 128 caracteres, sin carácter
 * nulo y sin reglas de composición. Se cuenta en code points para no penalizar caracteres
 * fuera del plano básico.
 */
final class PasswordPolicy {

    static final int MIN_LENGTH = 12;
    static final int MAX_LENGTH = 128;

    private PasswordPolicy() {
    }

    /** @throws com.integrate.common.exception.BusinessRuleException 422 sobre {@code field} si no cumple */
    static void check(String password, String field) {
        if (password == null) {
            throw AuthErrors.invalidField(field, "must not be null");
        }
        int length = password.codePointCount(0, password.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            throw AuthErrors.invalidField(field,
                    "size must be between " + MIN_LENGTH + " and " + MAX_LENGTH + " characters");
        }
        if (password.indexOf('\0') >= 0) {
            throw AuthErrors.invalidField(field, "must not contain null characters");
        }
    }
}
