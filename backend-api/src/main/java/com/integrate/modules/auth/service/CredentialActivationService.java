package com.integrate.modules.auth.service;

import java.time.Instant;
import java.util.UUID;

/** Activación asistida de cuentas {@code PENDING_RESET} con código de un solo uso (ADR-0007 §6). */
public interface CredentialActivationService {

    /**
     * Emite un código nuevo para {@code userId} y revoca el anterior si seguía vivo. Lo usa {@code catalog}
     * tras comprobar que {@code issuedBy} tiene jurisdicción sobre el anfitrión.
     *
     * @return el código en claro, que solo existe en esta respuesta
     * @throws com.integrate.common.exception.BusinessRuleException {@code RESOURCE_NOT_FOUND} si la cuenta no existe
     *         o está cerrada; {@code CATALOG_INVALID_STATUS_TRANSITION} (409) si ya está activa
     */
    IssuedActivationCode issue(UUID userId, UUID issuedBy);

    /**
     * Canjea el código por la contraseña elegida por el titular y habilita el login.
     *
     * @throws com.integrate.common.exception.BusinessRuleException {@code VALIDATION_FAILED} (422) si la contraseña
     *         no cumple la política; {@code AUTH_ACTIVATION_INVALID} (401), igual para correo inexistente, código
     *         erróneo, vencido, consumido o agotado
     */
    void activate(String email, String code, String newPassword);

    record IssuedActivationCode(String code, Instant expiresAt) {

        @Override
        public String toString() {
            // Nunca imprimir el código.
            return "IssuedActivationCode[expiresAt=" + expiresAt + "]";
        }
    }
}
