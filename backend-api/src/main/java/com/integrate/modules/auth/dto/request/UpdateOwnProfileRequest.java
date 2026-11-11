package com.integrate.modules.auth.dto.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
public record UpdateOwnProfileRequest(@NotBlank @Size(min = 3, max = 150) String fullName,
    @NotNull @Pattern(regexp = "^\\+[1-9][0-9]{7,14}$") String phone) {
    @Override public String toString() { return "UpdateOwnProfileRequest[redacted]"; }
}
