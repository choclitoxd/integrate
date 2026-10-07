package com.integrate.modules.catalog.dto.request;

import com.integrate.modules.auth.entity.ConsentPurpose;
import com.integrate.modules.auth.entity.DocType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

/**
 * Contrato {@code AssistedHostRegistrationRequest} (dtos_contrato_api.md §3.3, ADR-0007 §3). Los datos de
 * víctima se cifran antes de persistir y nunca se registran en logs.
 */
public record AssistedHostRegistrationRequest(
        @NotBlank @Size(min = 3, max = 150) String fullName,
        @NotNull DocType docType,
        @NotNull @Pattern(regexp = "^[A-Za-z0-9]{4,20}$") String docNumber,
        @NotNull @Pattern(regexp = "^\\+[1-9][0-9]{7,14}$") String phone,
        // Opcional: si falta, el servidor genera uno sintético.
        @Email @Size(max = 254)
        @Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "must be a valid email address")
        String email,
        UUID linkManagerId,
        @NotNull @Valid VictimData victimData,
        @NotNull @Valid AssistedConsent consent) {

    @Override
    public String toString() {
        return "AssistedHostRegistrationRequest[docType=" + docType + ", linkManagerId=" + linkManagerId + "]";
    }

    /** Datos sensibles (Ley 1448 de 2011). Se cifran con AES-256-GCM y nunca se devuelven. */
    public record VictimData(
            @NotNull Boolean victimCondition,
            @Size(max = 40) String ruvRegistrationNumber,
            @Size(max = 80) String victimizationType,
            @Size(max = 512) String supportDocS3Key) {

        @Override
        public String toString() {
            return "VictimData[redacted]";
        }
    }

    public record AssistedConsent(
            @NotBlank @Size(max = 20) String policyVersion,
            @NotEmpty Set<ConsentPurpose> purposes,
            @NotBlank @Size(max = 512) String physicalConsentFormS3Key) {
    }
}
