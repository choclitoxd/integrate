package com.integrate.modules.catalog.dto.request;

import com.integrate.modules.catalog.entity.Experience.ExperienceContent;
import com.integrate.modules.catalog.entity.PhysicalIntensity;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Set;

/**
 * Propuesta de experiencia del anfitrión (RF-05, ADR-0006 §4). Los límites replican los CHECK de
 * V3 (aforo 1–30, duración 15–4320 min, precio entero positivo) para responder 422 antes de la BD.
 */
public record ExperienceRequest(
        @NotBlank @Size(min = 5, max = 200) String title,
        @NotBlank @Size(max = 10_000) String description,
        @NotBlank @Size(max = 5_000) String safetyGuidelines,
        @NotNull PhysicalIntensity physicalIntensity,
        @Min(15) @Max(4320) int durationMinutes,
        @NotNull @Pattern(regexp = "^[0-9]{5}$") String municipalityCode,
        @Size(max = 150) String vereda,
        @NotBlank @Size(max = 500) String meetingPoint,
        @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 2, fraction = 6) BigDecimal latitude,
        @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 6) BigDecimal longitude,
        @Min(1) @Max(30) int maxCapacityPerSlot,
        @Positive @Max(999_999_999_999L) long unitPriceCop,
        @Size(max = 5) Set<@NotBlank String> categoryCodes) {

    public ExperienceContent toContent() {
        return new ExperienceContent(title.strip(), description.strip(), safetyGuidelines.strip(), physicalIntensity,
                durationMinutes, municipalityCode, vereda == null || vereda.isBlank() ? null : vereda.strip(),
                meetingPoint.strip(), latitude, longitude, maxCapacityPerSlot, unitPriceCop);
    }

    public Set<String> categoryCodesOrEmpty() {
        return categoryCodes == null ? Set.of() : categoryCodes;
    }
}
