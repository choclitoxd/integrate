package com.integrate.modules.booking.service.impl;

import com.integrate.common.audit.SensitiveAccessRecorder;
import com.integrate.common.crypto.SensitiveDataCipher;
import com.integrate.common.exception.BusinessRuleException;
import com.integrate.common.exception.ErrorCode;
import com.integrate.common.payload.PageResponse;
import com.integrate.modules.auth.entity.DocType;
import com.integrate.modules.auth.service.ConsentService;
import com.integrate.modules.auth.service.model.ClientInfo;
import com.integrate.modules.booking.entity.BookingParticipant;
import com.integrate.modules.booking.service.HostBookingService;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Acceso mínimo del anfitrión a reservas propias y EPS, ADR-0060. */
@Service
public class HostBookingServiceImpl implements HostBookingService {
    private static final String OWNED = """
            FROM booking.bookings b
            JOIN catalog.schedules s ON s.id = b.schedule_id
            JOIN catalog.experiences e ON e.id = s.experience_id
            JOIN catalog.host_profiles h ON h.id = e.host_id
            JOIN booking.booking_participants p ON p.booking_id = b.id AND p.is_holder
            WHERE h.user_id = ? AND h.deleted_at IS NULL AND e.deleted_at IS NULL AND b.status = 'CONFIRMED'
            """;
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private final JdbcTemplate jdbc;
    private final ConsentService consents;
    private final SensitiveDataCipher cipher;
    private final SensitiveAccessRecorder sensitiveAccess;

    public HostBookingServiceImpl(JdbcTemplate jdbc, ConsentService consents, SensitiveDataCipher cipher,
            SensitiveAccessRecorder sensitiveAccess) {
        this.jdbc = jdbc;
        this.consents = consents;
        this.cipher = cipher;
        this.sensitiveAccess = sensitiveAccess;
    }

    private record EpsRow(UUID touristId, String docType, String docNumber, byte[] eps, short keyVersion,
                          Long healthConsentId) { }

    @Transactional(readOnly = true)
    public PageResponse<Summary> list(UUID hostId, int page, int size, Filter filter) {
        StringBuilder scope = new StringBuilder(OWNED);
        List<Object> args = new ArrayList<>();
        args.add(hostId);
        if (filter.period() == Period.UPCOMING) {
            scope.append(" AND s.start_time >= CURRENT_TIMESTAMP AND s.status <> 'CANCELLED'");
        } else if (filter.period() == Period.HISTORY) {
            scope.append(" AND s.start_time < CURRENT_TIMESTAMP");
        }
        if (filter.date() != null) {
            scope.append(" AND s.start_time >= ? AND s.start_time < ?");
            args.add(java.sql.Timestamp.from(filter.date().atStartOfDay(BOGOTA).toInstant()));
            args.add(java.sql.Timestamp.from(filter.date().plusDays(1).atStartOfDay(BOGOTA).toInstant()));
        }
        if (filter.experience() != null && !filter.experience().isBlank()) {
            scope.append(" AND strpos(lower(e.title), lower(?)) > 0");
            args.add(filter.experience().strip());
        }
        Long count = jdbc.queryForObject("SELECT count(*) " + scope, Long.class, args.toArray());
        args.add(size);
        args.add((long) page * size);
        String order = filter.period() == Period.UPCOMING ? "ASC" : "DESC";
        List<Summary> rows = jdbc.query("""
                SELECT b.id, e.title, s.start_time, p.full_name, s.id, b.slots_count,
                       count(*) OVER (PARTITION BY s.id), sum(b.slots_count) OVER (PARTITION BY s.id)
                """ + scope + " ORDER BY s.start_time " + order + ", s.id, b.id LIMIT ? OFFSET ?",
                (rs, n) -> new Summary(rs.getObject(1, UUID.class), rs.getString(2),
                        rs.getTimestamp(3).toInstant(), rs.getString(4), rs.getObject(5, UUID.class),
                        rs.getInt(6), rs.getLong(7), rs.getLong(8)), args.toArray());
        long total = count == null ? 0 : count;
        return new PageResponse<>(rows, page, size, total, (int) Math.min(Integer.MAX_VALUE, (total + size - 1) / size));
    }

    @Transactional
    public HolderEps holderEps(UUID hostId, UUID bookingId, ClientInfo client) {
        List<EpsRow> rows = jdbc.query("SELECT b.tourist_id, p.doc_type, p.doc_number, p.eps_enc, "
                + "p.sensitive_key_version, b.health_consent_id " + OWNED + " AND b.id = ? FOR SHARE OF b, e, h",
                (rs, n) -> new EpsRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getBytes(4), rs.getShort(5), rs.getObject(6, Long.class)), hostId, bookingId);
        if (rows.isEmpty()) { throw BookingErrors.notFound(); }
        EpsRow row = rows.getFirst();
        if (row.healthConsentId() == null || consents.currentHealthGrant(row.touristId())
                .filter(grant -> row.healthConsentId().equals(grant.grantId())).isEmpty()) {
            throw forbidden();
        }
        String eps = row.eps() == null ? null : cipher.decrypt(row.eps(),
                BookingParticipant.epsAad(bookingId, DocType.valueOf(row.docType()), row.docNumber()), row.keyVersion());
        sensitiveAccess.record(new SensitiveAccessRecorder.Access("booking", "bookings", bookingId.toString(),
                "HOST_EMERGENCY", List.of("medicalEps"), List.of(bookingId.toString()), hostId, client.ipAddress(),
                client.userAgent()));
        return new HolderEps(eps);
    }

    private static BusinessRuleException forbidden() {
        return new BusinessRuleException(ErrorCode.RBAC_FORBIDDEN, "Health data disclosure is not authorized.");
    }
}
