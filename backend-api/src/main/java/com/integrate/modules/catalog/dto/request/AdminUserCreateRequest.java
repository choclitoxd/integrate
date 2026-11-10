package com.integrate.modules.catalog.dto.request;

import com.integrate.modules.auth.entity.DocType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Alta de una cuenta de turista por el administrador (ADR-0048 §2). Los patrones replican los de
 * {@code RegisterRequest}; el rol de anfitrión o de gestor se otorga después con sus propios endpoints.
 */
public record AdminUserCreateRequest(
        @NotBlank @Email @Size(max = 254)
        @Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "must be a valid email address")
        String email,
        @NotNull String password,
        @NotBlank @Size(min = 3, max = 150) String fullName,
        @NotNull DocType docType,
        @NotNull @Pattern(regexp = "^[A-Za-z0-9]{4,20}$") String docNumber,
        @NotNull @Pattern(regexp = "^\\+[1-9][0-9]{7,14}$") String phone) {

    @Override
    public String toString() {
        return "AdminUserCreateRequest[email=" + email + "]";
    }
}

