package com.integrate.modules.booking.entity;

import com.integrate.common.persistence.PgDomains;
import com.integrate.modules.auth.entity.DocType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Persona que ocupa un cupo ({@code booking.booking_participants}, E1). La EPS (dato de salud, B4) llega cifrada
 * y <b>no tiene getter</b>. El {@code id} es {@code IDENTITY} y no se conoce antes del {@code INSERT}, así que el
 * AAD usa la clave natural {@code (booking_id, doc_type, doc_number)}, única por reserva (ADR-0008 §4).
 */
@Entity
@Table(name = "booking_participants", schema = "booking")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookingParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false, updatable = false)
    private UUID bookingId;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false, length = 3, columnDefinition = PgDomains.DOC_TYPE)
    private DocType docType;

    @Column(name = "doc_number", nullable = false, length = 20, columnDefinition = PgDomains.DOC_NUMBER)
    private String docNumber;

    @Getter(AccessLevel.NONE)
    @Column(name = "eps_enc", nullable = false, columnDefinition = PgDomains.CIPHERTEXT)
    private byte[] epsEnc;

    @Column(name = "sensitive_key_version", nullable = false)
    private short sensitiveKeyVersion;

    @Column(name = "is_holder", nullable = false)
    private boolean holder;

    public static String epsAad(UUID bookingId, DocType docType, String docNumber) {
        return "booking.booking_participants.eps_enc:" + bookingId + ":" + docType + ":" + docNumber;
    }

    /** Participante con la EPS ya cifrada con {@link #epsAad(UUID, DocType, String)} y {@code keyVersion}. */
    public static BookingParticipant of(UUID bookingId, String fullName, DocType docType, String docNumber,
                                        byte[] epsEnc, short keyVersion, boolean holder) {
        BookingParticipant participant = new BookingParticipant();
        participant.bookingId = bookingId;
        participant.fullName = fullName;
        participant.docType = docType;
        participant.docNumber = docNumber;
        participant.epsEnc = epsEnc;
        participant.sensitiveKeyVersion = keyVersion;
        participant.holder = holder;
        return participant;
    }
}
