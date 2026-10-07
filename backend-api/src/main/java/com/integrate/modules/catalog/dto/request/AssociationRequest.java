package com.integrate.modules.catalog.dto.request;

import com.integrate.modules.catalog.entity.AssociationType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Alta de asociación comunitaria por el administrador (ADR-0006 §3). Patrones de los CHECK de V3. */
public record AssociationRequest(
        @NotNull @Pattern(regexp = "^[0-9]{6,12}-[0-9]$", message = "must look like 900123456-7") String nit,
        @NotNull @Pattern(regexp = "^[0-9]{3,20}$") String rntNumber,
        @NotBlank @Size(min = 3, max = 200) String legalName,
        @NotNull AssociationType associationType,
        @NotNull @Pattern(regexp = "^[0-9]{5}$") String municipalityCode,
        @Size(max = 250) String address,
        @NotBlank @Email @Size(max = 254) @Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") String contactEmail,
        @NotNull @Pattern(regexp = "^\\+[1-9][0-9]{7,14}$") String contactPhone) {
}
