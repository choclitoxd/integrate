package com.integrate.modules.catalog.dto.response;

import com.integrate.modules.auth.entity.DocType;
import com.integrate.modules.catalog.entity.AccreditationStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Detalle de una cuenta para el administrador (ADR-0043 §1.5): lo mismo que la búsqueda, con el correo siempre
 * enmascarado (aquí no hay criterio de búsqueda que lo justifique), el documento solo por sus 4 últimos caracteres y
 * el historial completo de roles.
 */
public record AdminUserDetailResponse(UUID userId, String fullName, String maskedEmail, String status,
                                      DocType docType, String docNumberLast4, List<String> roles,
                                      UUID hostProfileId, UUID linkManagerId, AccreditationStatus linkManagerStatus,
                                      List<RoleHistory> roleHistory) {

    /** {@code revokedAt} es {@code null} mientras el rol siga vigente. */
    public record RoleHistory(String role, Instant revokedAt, UUID revokedBy) {
    }
}
