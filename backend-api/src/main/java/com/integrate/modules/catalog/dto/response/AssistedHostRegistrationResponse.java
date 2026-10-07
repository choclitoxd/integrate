package com.integrate.modules.catalog.dto.response;

import com.integrate.modules.auth.entity.ConsentChannel;
import com.integrate.modules.auth.entity.ConsentPurpose;
import com.integrate.modules.auth.entity.CredentialStatus;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Contrato {@code AssistedHostRegistrationResponse} (dtos_contrato_api.md §3.4). Nunca incluye datos de víctima. */
public record AssistedHostRegistrationResponse(
        UUID userId, UUID hostProfileId, UUID associationId, long assignmentId, UUID linkManagerId, String email,
        boolean emailIsSynthetic, List<String> roles, CredentialStatus credentialStatus, ConsentReceipt consent) {

    public record ConsentReceipt(ConsentChannel channel, String policyVersion, Set<ConsentPurpose> purposes,
                                 UUID capturedBy, Instant capturedAt) {
    }
}
