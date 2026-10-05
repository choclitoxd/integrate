package com.integrate.modules.auth.entity;

/** Valores de {@code ck_users_credential_status} (V10). */
public enum CredentialStatus {
    ACTIVE,
    /** Anfitrión registrado por un gestor con contraseña aleatoria: no puede iniciar sesión. */
    PENDING_RESET
}
