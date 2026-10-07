package com.integrate.modules.auth.service.model;

import com.integrate.modules.auth.entity.ConsentPurpose;
import com.integrate.modules.auth.entity.CredentialStatus;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Cuenta creada por {@code AssistedAccountService}; nunca incluye la contraseña aleatoria. */
public record ProvisionedHost(UUID userId, String email, boolean emailIsSynthetic, List<String> roles,
                              CredentialStatus credentialStatus, String policyVersion, Set<ConsentPurpose> purposes,
                              UUID capturedBy, Instant capturedAt) {
}
