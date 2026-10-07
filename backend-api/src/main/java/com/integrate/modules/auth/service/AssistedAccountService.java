package com.integrate.modules.auth.service;

import com.integrate.modules.auth.service.model.AssistedHostAccount;
import com.integrate.modules.auth.service.model.ClientInfo;
import com.integrate.modules.auth.service.model.ProvisionedHost;

/**
 * Interfaz pública de {@code auth} para el registro asistido (ADR-0007 §3). La usa {@code catalog},
 * que orquesta el enrolamiento; participa en la transacción del invocador.
 */
public interface AssistedAccountService {

    /** Dominio de los correos sintéticos de anfitriones sin correo propio (contrato §3.3, ADR-0007 §3). */
    String SYNTHETIC_EMAIL_DOMAIN = "comunidad.integrate.co";

    /**
     * Crea la cuenta del anfitrión con {@code PENDING_RESET} y una contraseña aleatoria que nadie conoce,
     * le otorga {@code HOST} y registra el consentimiento {@code ASSISTED_PROXY} capturado por
     * {@code account.capturedBy()} desde la terminal {@code client}.
     *
     * @throws com.integrate.common.exception.BusinessRuleException {@code VALIDATION_FAILED} (422) si la política
     *         no es la vigente o las finalidades no son exactamente {@code PERSONAL_DATA} y {@code SENSITIVE_VICTIM};
     *         {@code ENROLLMENT_DOCUMENT_INVALID} (422) si el formato físico ya respalda otro consentimiento;
     *         {@code AUTH_USER_ALREADY_EXISTS} (409) si el correo o el documento ya están registrados
     */
    ProvisionedHost provisionHost(AssistedHostAccount account, ClientInfo client);
}
