package com.integrate.modules.offline.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Acceso a datos del módulo {@code offline} (ADR-0032). Lee en solo lectura los esquemas {@code voucher},
 * {@code booking} y {@code catalog} para armar el padrón de una asociación: es la lectura cruzada documentada del
 * módulo, igual que el barrido de {@code voucher}. Las escrituras son solo sobre {@code offline.*}.
 */
@Repository
@RequiredArgsConstructor
public class OfflineQueries {

    /** Prefijo del AAD del contacto de emergencia: debe coincidir con {@code Booking.aad(column, bookingId)}. */
    public static final String EMERGENCY_AAD_PREFIX = "booking.bookings.";

    private final JdbcClient jdbc;

    public record ManagerScope(UUID managerId, UUID associationId) {
    }

    public record DeviceRow(UUID id, UUID managerId, String status) {
    }

    public record SigningKeyRow(short id, String kid, String algorithm, String publicKeyPem, String status,
                                Instant validFrom) {
    }

    public record RosterRow(UUID voucherId, UUID bookingId, UUID scheduleId, String experienceTitle,
                            String shortCode, String tokenSha256, short signingKeyId, String signingKeyKid,
                            int slotsCount, String holderName, String holderDocument, byte[] contactNameEnc,
                            byte[] contactPhoneEnc, short contactKeyVersion) {
    }

    public record ParticipantRow(UUID bookingId, String fullName, String docType, String docNumber) {
    }

    public record RevocationRow(UUID voucherId, String shortCode, Instant revokedAt, String reason) {
    }

    public record VoucherLock(UUID voucherId, UUID bookingId, String status, Instant expiresAt, Instant consumedAt,
                              String bookingStatus, int slotsCount, Instant scheduleStart, String experienceTitle,
                              String holderName) {
    }

    // ------------------------------------------------------------------ gestor y dispositivo

    public Optional<ManagerScope> activeManager(UUID userId) {
        return jdbc.sql("""
                SELECT id, association_id FROM catalog.link_manager_profiles
                 WHERE user_id = :userId AND deleted_at IS NULL AND accreditation_status = 'ACTIVE'""")
                .param("userId", userId)
                .query((rs, n) -> new ManagerScope(rs.getObject("id", UUID.class),
                        rs.getObject("association_id", UUID.class)))
                .optional();
    }

    public Optional<DeviceRow> findDevice(String identifier) {
        return jdbc.sql("SELECT id, manager_id, status FROM offline.devices WHERE device_identifier = :identifier")
                .param("identifier", identifier)
                .query((rs, n) -> new DeviceRow(rs.getObject("id", UUID.class), rs.getObject("manager_id", UUID.class),
                        rs.getString("status")))
                .optional();
    }

    /** Idempotente ante dos primeros usos simultáneos: el perdedor simplemente lee la fila del ganador. */
    public void insertDeviceIfAbsent(String identifier, UUID managerId, String platform) {
        jdbc.sql("""
                INSERT INTO offline.devices (device_identifier, manager_id, platform)
                VALUES (:identifier, :managerId, :platform) ON CONFLICT (device_identifier) DO NOTHING""")
                .param("identifier", identifier).param("managerId", managerId).param("platform", platform)
                .update();
    }

    public void touchDevice(UUID deviceId, Instant at) {
        jdbc.sql("UPDATE offline.devices SET last_seen_at = :at WHERE id = :id")
                .param("at", at.atOffset(java.time.ZoneOffset.UTC)).param("id", deviceId).update();
    }

    // -------------------------------------------------------------------------- padrón

    /** Vouchers {@code ISSUED} de reservas {@code CONFIRMED} cuya franja cae en [from, to) y es de la asociación. */
    public List<RosterRow> roster(UUID associationId, Instant from, Instant to, UUID scheduleId, int limit) {
        return jdbc.sql("""
                SELECT v.id AS voucher_id, v.booking_id, b.schedule_id, e.title, v.short_code, v.token_sha256,
                       v.signing_key_id, k.kid, b.slots_count, hp.full_name,
                       hp.doc_type::text || ':' || hp.doc_number::text AS holder_document,
                       b.emergency_contact_name_enc, b.emergency_contact_phone_enc, b.sensitive_key_version
                  FROM voucher.vouchers v
                  JOIN booking.bookings b ON b.id = v.booking_id
                  JOIN catalog.schedules s ON s.id = b.schedule_id
                  JOIN catalog.experiences e ON e.id = s.experience_id
                  JOIN catalog.host_profiles h ON h.id = e.host_id
                  JOIN voucher.signing_keys k ON k.id = v.signing_key_id
                  JOIN booking.booking_participants hp ON hp.booking_id = b.id AND hp.is_holder
                 WHERE v.status = 'ISSUED' AND b.status = 'CONFIRMED'
                   AND h.association_id = :association
                   AND s.start_time >= :from AND s.start_time < :to
                   AND (CAST(:scheduleId AS uuid) IS NULL OR s.id = CAST(:scheduleId AS uuid))
                 ORDER BY s.start_time, v.short_code
                 LIMIT :limit""")
                .param("association", associationId).param("from", utc(from)).param("to", utc(to))
                .param("scheduleId", scheduleId, java.sql.Types.OTHER).param("limit", limit)
                .query((rs, n) -> new RosterRow(rs.getObject("voucher_id", UUID.class),
                        rs.getObject("booking_id", UUID.class), rs.getObject("schedule_id", UUID.class),
                        rs.getString("title"), rs.getString("short_code"), rs.getString("token_sha256"),
                        rs.getShort("signing_key_id"), rs.getString("kid"), rs.getInt("slots_count"),
                        rs.getString("full_name"), rs.getString("holder_document"),
                        rs.getBytes("emergency_contact_name_enc"), rs.getBytes("emergency_contact_phone_enc"),
                        rs.getShort("sensitive_key_version")))
                .list();
    }

    public Map<UUID, List<ParticipantRow>> participants(Collection<UUID> bookingIds) {
        if (bookingIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<ParticipantRow>> byBooking = new LinkedHashMap<>();
        jdbc.sql("""
                SELECT booking_id, full_name, doc_type::text AS doc_type, doc_number::text AS doc_number
                  FROM booking.booking_participants WHERE booking_id IN (:ids)
                 ORDER BY booking_id, is_holder DESC, id""")
                .param("ids", bookingIds)
                .query((rs, n) -> new ParticipantRow(rs.getObject("booking_id", UUID.class),
                        rs.getString("full_name"), rs.getString("doc_type"), rs.getString("doc_number")))
                .list()
                .forEach(p -> byBooking.computeIfAbsent(p.bookingId(), id -> new java.util.ArrayList<>()).add(p));
        return byBooking;
    }

    /** La llave ACTIVE y las RETIRED usadas por el padrón; las COMPROMISED nunca se envían. */
    public List<SigningKeyRow> signingKeys(Collection<Short> usedKeyIds) {
        return jdbc.sql("""
                SELECT id, kid, algorithm, public_key_pem, status, valid_from FROM voucher.signing_keys
                 WHERE status = 'ACTIVE' OR (status = 'RETIRED' AND id IN (:used))
                 ORDER BY id""")
                .param("used", usedKeyIds.isEmpty() ? List.of((short) -1) : usedKeyIds)
                .query((rs, n) -> new SigningKeyRow(rs.getShort("id"), rs.getString("kid"),
                        rs.getString("algorithm"), rs.getString("public_key_pem"), rs.getString("status"),
                        instant(rs, "valid_from")))
                .list();
    }

    /**
     * Vouchers revocados de la asociación con {@code revoked_at >= since} (todos si {@code since} es nulo) cuya franja
     * no empezó antes de {@code scheduleNotBefore}: lo ya pasado no interesa al dispositivo.
     */
    public List<RevocationRow> revocations(UUID associationId, Instant since, Instant scheduleNotBefore) {
        return jdbc.sql("""
                SELECT v.id, v.short_code, v.revoked_at, v.revocation_reason
                  FROM voucher.vouchers v
                  JOIN booking.bookings b ON b.id = v.booking_id
                  JOIN catalog.schedules s ON s.id = b.schedule_id
                  JOIN catalog.experiences e ON e.id = s.experience_id
                  JOIN catalog.host_profiles h ON h.id = e.host_id
                 WHERE v.revoked_at IS NOT NULL AND h.association_id = :association
                   AND s.start_time >= :notBefore
                   AND (CAST(:since AS timestamptz) IS NULL OR v.revoked_at >= CAST(:since AS timestamptz))
                 ORDER BY v.revoked_at, v.short_code""")
                .param("association", associationId).param("notBefore", utc(scheduleNotBefore))
                .param("since", since == null ? null : utc(since), java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
                .query((rs, n) -> new RevocationRow(rs.getObject("id", UUID.class), rs.getString("short_code"),
                        instant(rs, "revoked_at"), rs.getString("revocation_reason")))
                .list();
    }

    public void insertRosterDownload(UUID deviceId, UUID associationId, java.time.LocalDate rosterDate,
                                     short signingKeyId, int vouchersIncluded, Instant revocationsWatermark) {
        jdbc.sql("""
                INSERT INTO offline.roster_downloads (device_id, association_id, roster_date, signing_key_id,
                                                      vouchers_included, revocations_watermark)
                VALUES (:device, :association, :date, :key, :count, :watermark)""")
                .param("device", deviceId).param("association", associationId).param("date", rosterDate)
                .param("key", signingKeyId).param("count", vouchersIncluded)
                .param("watermark", revocationsWatermark == null ? null : utc(revocationsWatermark),
                        java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
                .update();
    }

    // -------------------------------------------------------------------------- canje

    /**
     * Bloquea la fila del voucher de un {@code shortCode} de la asociación. {@code FOR NO KEY UPDATE} y no
     * {@code FOR UPDATE}: el canje solo cambia columnas no clave y no debe bloquear las FK de las filas de
     * evidencia que otros intentos insertan sobre el mismo voucher.
     */
    public Optional<VoucherLock> lockVoucher(String shortCode, UUID associationId) {
        return jdbc.sql("""
                SELECT v.id, v.booking_id, v.status, v.expires_at, v.consumed_at, b.status AS booking_status,
                       b.slots_count, s.start_time, e.title, hp.full_name
                  FROM voucher.vouchers v
                  JOIN booking.bookings b ON b.id = v.booking_id
                  JOIN catalog.schedules s ON s.id = b.schedule_id
                  JOIN catalog.experiences e ON e.id = s.experience_id
                  JOIN catalog.host_profiles h ON h.id = e.host_id
                  LEFT JOIN booking.booking_participants hp ON hp.booking_id = b.id AND hp.is_holder
                 WHERE v.short_code = :code AND h.association_id = :association
                   FOR NO KEY UPDATE OF v""")
                .param("code", shortCode).param("association", associationId)
                .query((rs, n) -> new VoucherLock(rs.getObject("id", UUID.class),
                        rs.getObject("booking_id", UUID.class), rs.getString("status"),
                        instant(rs, "expires_at"), instantOrNull(rs, "consumed_at"), rs.getString("booking_status"),
                        rs.getInt("slots_count"), instant(rs, "start_time"), rs.getString("title"),
                        rs.getString("full_name")))
                .optional();
    }

    /** Evidencia append-only; un reintento con el mismo (voucher, dispositivo, instante) no se duplica. */
    public void insertManualValidation(UUID voucherId, UUID deviceId, UUID validatedBy, Instant scannedAt,
                                       String localResult, String serverOutcome) {
        jdbc.sql("""
                INSERT INTO offline.offline_validations (voucher_id, channel, device_id, validated_by, scanned_at,
                                                         local_result, server_outcome)
                VALUES (:voucher, 'MANUAL_CODE', :device, :by, :at, :local, :outcome)
                ON CONFLICT ON CONSTRAINT uq_offline_validations_scan DO NOTHING""")
                .param("voucher", voucherId).param("device", deviceId, java.sql.Types.OTHER).param("by", validatedBy)
                .param("at", utc(scannedAt)).param("local", localResult).param("outcome", serverOutcome)
                .update();
    }

    // ------------------------------------------------------------------------- utilidades

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(java.time.ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class).toInstant();
    }

    private static Instant instantOrNull(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
