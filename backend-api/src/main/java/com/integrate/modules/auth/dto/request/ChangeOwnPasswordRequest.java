package com.integrate.modules.auth.dto.request;
import jakarta.validation.constraints.NotNull;
public record ChangeOwnPasswordRequest(@NotNull String currentPassword, @NotNull String newPassword) {
    @Override public String toString() { return "ChangeOwnPasswordRequest[redacted]"; }
}
