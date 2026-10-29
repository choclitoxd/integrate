package com.integrate.modules.offline.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Padrón de la jornada para la PWA del Gestor (ADR-0032 §2); nunca incluye la EPS ni datos de salud. */
public record RosterResponse(LocalDate rosterDate, Instant generatedAt, UUID associationId,
                             List<SigningKey> signingKeys, List<Voucher> vouchers, RevocationsResponse revocations) {

    public record SigningKey(String kid, String algorithm, String publicKeyPem, String status, Instant validFrom) {
    }

    public record Voucher(UUID voucherId, UUID jti, UUID bookingId, UUID scheduleId, String experienceTitle, String shortCode,
                          String tokenSha256, String signingKeyKid, String holderName, String holderDocument,
                          int slotsCount, List<Participant> participants, EmergencyContact emergencyContact) {
    }

    public record Participant(String fullName, String docType, String docNumber) {
    }

    public record EmergencyContact(String name, String phone) {
    }
}
