package com.integrate.modules.auth.entity;

/** Valores de {@code ck_login_attempts_reason} (V2). */
public enum LoginFailureReason {
    BAD_CREDENTIALS,
    ACCOUNT_LOCKED,
    ACCOUNT_SUSPENDED,
    UNKNOWN_USER
}
