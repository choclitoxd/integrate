package com.integrate.modules.auth.entity;

/** Valores de {@code ck_refresh_tokens_revoke} (V2). */
public enum RevokeReason {
    LOGOUT,
    ROTATED,
    REUSE_DETECTED,
    ADMIN,
    PASSWORD_CHANGE
}
