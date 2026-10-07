package com.integrate.modules.catalog.service;

import com.integrate.modules.auth.service.model.ClientInfo;
import com.integrate.modules.catalog.dto.request.AssistedHostRegistrationRequest;
import com.integrate.modules.catalog.dto.request.DocumentKind;
import com.integrate.modules.catalog.dto.response.AssistedHostRegistrationResponse;
import com.integrate.modules.catalog.dto.response.DocumentUploadResponse;
import java.util.UUID;

/**
 * Enrolamiento asistido de anfitriones por un gestor acreditado o un administrador (RF-06 en lo relativo
 * a la identidad, ADR-0007 §3 y §5).
 */
public interface AssistedEnrollmentService {

    /**
     * URL prefirmada para subir un PDF del enrolamiento. La llave la genera el servidor.
     *
     * @throws com.integrate.common.exception.BusinessRuleException {@code ENROLLMENT_MANAGER_NOT_ACTIVE} (403) si un
     *         gestor sin acreditación vigente la pide
     */
    DocumentUploadResponse presignUpload(UUID actorUserId, boolean actorIsAdmin, DocumentKind kind);

    /**
     * Crea cuenta, consentimiento {@code ASSISTED_PROXY}, perfil con datos de víctima cifrados y vínculo con el
     * gestor, en una sola transacción. La asociación del anfitrión es la del gestor.
     *
     * @throws com.integrate.common.exception.BusinessRuleException {@code ENROLLMENT_MANAGER_NOT_ACTIVE} (403),
     *         {@code RESOURCE_NOT_FOUND} (gestor indicado por el administrador), {@code CATALOG_INVALID_STATUS_TRANSITION}
     *         (409, gestor indicado no activo), {@code CATALOG_HOST_PROFILE_EXISTS} (409, asociación inactiva),
     *         {@code AUTH_USER_ALREADY_EXISTS} (409), {@code ENROLLMENT_DOCUMENT_INVALID} o {@code VALIDATION_FAILED} (422)
     */
    AssistedHostRegistrationResponse register(UUID actorUserId, boolean actorIsAdmin,
                                              AssistedHostRegistrationRequest request, ClientInfo client);
}
