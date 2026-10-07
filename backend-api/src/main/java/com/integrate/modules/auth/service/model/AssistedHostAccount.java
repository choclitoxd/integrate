package com.integrate.modules.auth.service.model;

import com.integrate.modules.auth.entity.ConsentPurpose;
import com.integrate.modules.auth.entity.DocType;
import java.util.Set;
import java.util.UUID;

/**
 * Datos de identidad y consentimiento de un anfitrión registrado por un gestor (ADR-0007 §3).
 * {@code email} es opcional: si falta, se genera uno sintético a partir del documento.
 */
public record AssistedHostAccount(String email, String fullName, DocType docType, String docNumber, String phone,
                                  String policyVersion, Set<ConsentPurpose> purposes, String physicalConsentFormS3Key,
                                  UUID capturedBy) {
}
