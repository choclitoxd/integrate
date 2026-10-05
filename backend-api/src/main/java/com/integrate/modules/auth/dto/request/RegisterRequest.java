package com.integrate.modules.auth.dto.request;

import com.integrate.modules.auth.entity.DocType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Autorregistro de turista ({@code POST /api/v1/auth/register}, ADR-0005 §6). No admite rol:
 * siempre crea {@code TOURIST}. Los patrones replican el contrato y los dominios de V1 para que
 * ningún valor llegue a violar un {@code CHECK} de PostgreSQL.
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 254)
        @Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "must be a valid email address")
        String email,
        // La política de longitud (12–128) la aplica PasswordPolicy para compartirla con el bootstrap.
        @NotNull String password,
        @NotBlank @Size(min = 3, max = 150) String fullName,
        @NotNull DocType docType,
        @NotNull @Pattern(regexp = "^[A-Za-z0-9]{4,20}$") String docNumber,
        @NotNull @Pattern(regexp = "^\\+[1-9][0-9]{7,14}$") String phone,
        @NotNull @Valid ConsentAcceptance consent) {

    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + "]";
    }
}
