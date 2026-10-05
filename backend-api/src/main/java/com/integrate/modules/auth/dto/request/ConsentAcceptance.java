package com.integrate.modules.auth.dto.request;

import com.integrate.modules.auth.entity.ConsentPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * Aceptación expresa de finalidades sobre una versión de la política (RF-02). Se usa en el
 * registro ({@code consent}) y en {@code POST /api/v1/me/consents}. La versión debe ser la vigente.
 */
public record ConsentAcceptance(
        @NotBlank @Size(max = 20) String policyVersion,
        @NotEmpty Set<@NotNull ConsentPurpose> purposes) {
}
