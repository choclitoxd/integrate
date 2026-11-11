package com.integrate.modules.catalog.dto.request;

import com.integrate.modules.auth.entity.DocType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Actualización administrativa de una persona (ADR-0057 §1).
 */
public record AdminUserUpdateRequest(
        @NotBlank @Size(min = 3, max = 150) String fullName,
        @NotNull DocType docType,
        @NotNull @Pattern(regexp = "^[A-Za-z0-9]{4,20}$") String docNumber,
        @NotNull @Pattern(regexp = "^\\+[1-9][0-9]{7,14}$") String phone) {

    @Override
    public String toString() {
        return "AdminUserUpdateRequest[fullName=" + fullName + "]";
    }
}

