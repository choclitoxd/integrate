package com.integrate.modules.offline.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Lote de validaciones capturadas sin conexión (contrato §7.1, ADR-0033). */
public record OfflineSyncBatchRequest(
        @NotNull UUID syncBatchId,
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9._-]{4,64}$") String deviceId,
        @NotNull Instant deviceSentAt,
        @NotNull @Size(min = 1, max = 100) List<@Valid @NotNull Validation> validations) {

    public enum Channel {
        OFFLINE_PWA, MANUAL_CODE
    }

    /**
     * @param voucherId     el {@code jti} del voucher (lo único que la PWA lee del QR sin conexión)
     * @param signatureHash hex de 64 caracteres; obligatorio en {@code OFFLINE_PWA}, se omite en {@code MANUAL_CODE}
     */
    public record Validation(
            @NotNull UUID localValidationId,
            @NotNull UUID voucherId,
            @NotNull Instant scannedAt,
            @Pattern(regexp = "^[0-9a-fA-F]{64}$") String signatureHash,
            @NotNull @Pattern(regexp = "^[A-HJ-NP-Z2-9]{8}$") String shortCode,
            @NotNull Channel channel) {
    }
}