package com.integrate.modules.booking.entity;

import com.integrate.common.persistence.PgDomains;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

/**
 * Reserva de N cupos de una franja ({@code booking.bookings}, ADR-0008). Nace {@code PENDING} dentro de la
 * transacción de bloqueo pesimista. El identificador se asigna en la aplicación porque forma parte del AAD del
 * cifrado del contacto de emergencia, que <b>no tiene getter</b>. {@code currency} no se mapea: su único valor
 * es el {@code DEFAULT 'COP'} de la columna.
 */
@Entity
@Table(name = "bookings", schema = "booking")
@DynamicUpdate
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Booking {

    @Id
    private UUID id;

    @Column(name = "tourist_id", nullable = false, updatable = false)
    private UUID touristId;

    @Column(name = "schedule_id", nullable = false, updatable = false)
    private UUID scheduleId;

    @Column(name = "slots_count", nullable = false, updatable = false)
    private short slotsCount;

    @Column(name = "unit_price_cop", nullable = false, updatable = false, precision = 14, scale = 2,
            columnDefinition = PgDomains.COP_AMOUNT)
    private BigDecimal unitPriceCop;

    @Column(name = "total_amount_cop", nullable = false, updatable = false, precision = 14, scale = 2,
            columnDefinition = PgDomains.COP_AMOUNT)
    private BigDecimal totalAmountCop;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BookingStatus status;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "confirmed_at", insertable = false, updatable = false)
    private Instant confirmedAt;

    @Getter(AccessLevel.NONE)
    @Column(name = "emergency_contact_name_enc", nullable = false, updatable = false,
            columnDefinition = PgDomains.CIPHERTEXT)
    private byte[] emergencyContactNameEnc;

    @Getter(AccessLevel.NONE)
    @Column(name = "emergency_contact_phone_enc", nullable = false, updatable = false,
            columnDefinition = PgDomains.CIPHERTEXT)
    private byte[] emergencyContactPhoneEnc;

    @Column(name = "sensitive_key_version", nullable = false, updatable = false)
    private short sensitiveKeyVersion;

    @Column(name = "idempotency_key", updatable = false)
    private UUID idempotencyKey;

    @Column(name = "request_sha256", length = 64, updatable = false, columnDefinition = PgDomains.SHA256_HEX)
    private String requestSha256;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** AAD de una columna cifrada de esta fila: el ciphertext no descifra en otra fila ni columna. */
    public static String aad(String column, UUID bookingId) {
        return "booking.bookings." + column + ":" + bookingId;
    }

    /**
     * Reserva {@code PENDING} recién bloqueada. El contacto de emergencia llega ya cifrado con
     * {@link #aad(String, UUID)} y {@code keyVersion}.
     */
    public static Booking hold(UUID id, UUID touristId, UUID scheduleId, int slots, BigDecimal unitPriceCop,
                               Instant createdAt, Instant expiresAt, EmergencyCiphertexts emergencyContact,
                               UUID idempotencyKey, String requestSha256) {
        Booking booking = new Booking();
        booking.id = Objects.requireNonNull(id, "id");
        booking.touristId = touristId;
        booking.scheduleId = scheduleId;
        booking.slotsCount = (short) slots;
        booking.unitPriceCop = unitPriceCop;
        booking.totalAmountCop = unitPriceCop.multiply(BigDecimal.valueOf(slots));
        booking.status = BookingStatus.PENDING;
        booking.createdAt = createdAt;
        booking.expiresAt = expiresAt;
        booking.emergencyContactNameEnc = emergencyContact.nameEnc();
        booking.emergencyContactPhoneEnc = emergencyContact.phoneEnc();
        booking.sensitiveKeyVersion = emergencyContact.keyVersion();
        booking.idempotencyKey = idempotencyKey;
        booking.requestSha256 = requestSha256;
        return booking;
    }

    /** Vencimiento del TTL sin pago (RF-11). El trigger valida la transición y escribe el historial. */
    public void expire() {
        moveFrom(BookingStatus.PENDING, BookingStatus.EXPIRED);
    }

    /** Pago aprobado (RF-12). El trigger fija {@code confirmed_at}. */
    public void confirm() {
        moveFrom(BookingStatus.PENDING, BookingStatus.CONFIRMED);
    }

    /** Pago rechazado por la pasarela (RF-12): la reserva se pierde y sus cupos se devuelven. */
    public void failPayment() {
        moveFrom(BookingStatus.PENDING, BookingStatus.PAYMENT_FAILED);
    }

    /** Servicio prestado y canjeado en campo (ADR-0032 §4). */
    public void execute() {
        moveFrom(BookingStatus.CONFIRMED, BookingStatus.EXECUTED);
    }

    /** Pago anulado sobre una reserva aún {@code PENDING} (F5, V10): sus cupos se devuelven. */
    public void voidPending() {
        moveFrom(BookingStatus.PENDING, BookingStatus.CANCELLED_REFUNDED);
    }

    private void moveFrom(BookingStatus expected, BookingStatus target) {
        if (status != expected) {
            throw new IllegalStateException("Booking " + id + " is " + status + ", cannot move to " + target);
        }
        this.status = target;
    }

    /** Contacto de emergencia cifrado con AES-256-GCM (B4); ambas columnas con la misma versión de llave. */
    public record EmergencyCiphertexts(byte[] nameEnc, byte[] phoneEnc, short keyVersion) {
    }
}
