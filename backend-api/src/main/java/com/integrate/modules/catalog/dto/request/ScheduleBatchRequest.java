package com.integrate.modules.catalog.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Lote atómico de 1 a 60 franjas (ADR-0006 §5). Las horas son <b>locales</b> ({@code America/Bogota},
 * sin offset); el servidor las convierte a UTC.
 */
public record ScheduleBatchRequest(@NotEmpty @Size(max = 60) List<@NotNull @Valid Slot> schedules) {

    public record Slot(
            @NotNull LocalDateTime localStart,
            @NotNull LocalDateTime localEnd,
            @Min(1) @Max(30) int totalSlots) {
    }
}
