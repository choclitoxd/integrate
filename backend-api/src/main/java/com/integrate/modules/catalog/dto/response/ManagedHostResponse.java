package com.integrate.modules.catalog.dto.response;

import com.integrate.modules.auth.entity.CredentialStatus;
import com.integrate.modules.auth.entity.DocType;
import java.time.Instant;
import java.util.UUID;

/**
 * Anfitrión bajo la jurisdicción del gestor (ADR-0007 §1). Solo datos de contacto: el documento se
 * muestra con sus 4 últimos caracteres y nunca hay datos de víctima.
 */
public record ManagedHostResponse(UUID hostProfileId, UUID userId, String fullName, String email,
                                  boolean emailIsSynthetic, String phone, DocType docType, String docNumberLast4,
                                  CredentialStatus credentialStatus, Instant assignedSince) {
}
