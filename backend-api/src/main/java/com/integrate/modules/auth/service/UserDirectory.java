package com.integrate.modules.auth.service;

import com.integrate.modules.auth.entity.CredentialStatus;
import com.integrate.modules.auth.entity.DocType;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Lectura de datos de contacto de usuarios para otros módulos (guía §5.1). La usa {@code catalog}
 * para el listado de anfitriones del gestor (ADR-0007 §1). Nunca expone credenciales.
 */
public interface UserDirectory {

    /** Resumen de los usuarios existentes entre {@code userIds}; los inexistentes no aparecen. */
    Map<UUID, UserSummary> findSummaries(Collection<UUID> userIds);

    record UserSummary(UUID userId, String fullName, String email, String phone, DocType docType, String docNumber,
                       CredentialStatus credentialStatus) {
    }
}
