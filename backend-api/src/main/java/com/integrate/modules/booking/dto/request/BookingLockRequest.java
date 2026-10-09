package com.integrate.modules.booking.dto.request;

import com.integrate.modules.auth.entity.DocType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/v1/bookings/lock} (contrato §5.1). Las reglas entre campos (un participante por
 * cupo, documentos únicos, un solo titular) las valida el servicio con 422 antes de abrir la transacción.
 * {@link #toString()} no expone datos de salud ni de contacto.
 */
public record BookingLockRequest(
        @NotNull UUID scheduleId,
        @NotNull @Min(1) @Max(10) Integer requestedSlots,
        @NotNull @Valid EmergencyContact emergencyContact,
        @NotNull @Size(min = 1, max = 10) List<@NotNull @Valid Participant> participants) {

    public record EmergencyContact(
            @NotBlank @Size(min = 3, max = 150) String fullName,
            @NotNull @Pattern(regexp = "^\\+[1-9][0-9]{7,14}$") String phone) {

        @Override
        public String toString() {
            return "EmergencyContact[redacted]";
        }
    }

    public record Participant(
            @NotBlank @Size(min = 3, max = 150) String fullName,
            @NotNull DocType docType,
            @NotNull @Pattern(regexp = "^[A-Za-z0-9]{4,20}$") String docNumber,
            @NotBlank @Size(min = 2, max = 120) String medicalEps,
            @NotNull Boolean isHolder) {

        @Override
        public String toString() {
            return "Participant[redacted]";
        }
    }

    @Override
    public String toString() {
        return "BookingLockRequest[scheduleId=" + scheduleId + ", requestedSlots=" + requestedSlots + "]";
    }
}
