package com.integrate.modules.booking.service.impl;

import com.integrate.modules.booking.dto.request.BookingLockRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code request_sha256} del lock (ADR-0008 §3): SHA-256 en hexadecimal minúscula de la serialización canónica
 * del cuerpo ya validado. Los campos van en orden de declaración, las cadenas recortadas y los participantes en
 * el orden recibido: un reintento idéntico produce el mismo hash y cualquier cambio de contenido, otro.
 */
@Component
@RequiredArgsConstructor
class BookingRequestHasher {

    private final JsonMapper jsonMapper;

    String hash(BookingLockRequest request) {
        Canonical canonical = new Canonical(request.scheduleId(), request.requestedSlots(),
                strip(request.emergencyContact().fullName()), strip(request.emergencyContact().phone()),
                request.participants().stream()
                        .map(p -> new CanonicalParticipant(strip(p.fullName()), p.docType().name(),
                                strip(p.docNumber()), strip(p.medicalEps()), p.isHolder()))
                        .toList());
        byte[] json = jsonMapper.writeValueAsString(canonical).getBytes(StandardCharsets.UTF_8);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is mandatory in every JRE", e);
        }
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }

    record Canonical(UUID scheduleId, int requestedSlots, String emergencyContactName,
                     String emergencyContactPhone, List<CanonicalParticipant> participants) {
    }

    record CanonicalParticipant(String fullName, String docType, String docNumber, String medicalEps,
                                boolean isHolder) {
    }
}
