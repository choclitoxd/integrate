package com.integrate.modules.offline.repository;

import com.integrate.common.payload.PageResponse;
import com.integrate.modules.offline.dto.response.IncidentResponse;
import com.integrate.modules.offline.dto.response.IncidentResponse.Severity;
import com.integrate.modules.offline.dto.response.IncidentResponse.Status;
import com.integrate.modules.offline.dto.response.IncidentResponse.Type;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class IncidentQueries {

    private static final String SELECT = """
            SELECT i.*, v.id AS voucher_id, v.booking_id
              FROM offline.sync_incidents i
              JOIN offline.offline_validations ov ON ov.id = i.validation_id
              JOIN voucher.vouchers v ON v.id = ov.voucher_id
            """;
    private static final String FILTER = """
             WHERE (:status IS NULL OR i.status = :status)
               AND (:severity IS NULL OR i.severity = :severity)
               AND (:type IS NULL OR i.incident_type = :type)
            """;
    private final JdbcClient jdbc;

    public PageResponse<IncidentResponse> list(Status status, Severity severity, Type type, Pageable page) {
        var rows = filter(SELECT + FILTER + " ORDER BY i.created_at DESC, i.id LIMIT :limit OFFSET :offset",
                status, severity, type).param("limit", page.getPageSize()).param("offset", page.getOffset())
                .query(IncidentQueries::map).list();
        long total = filter("SELECT count(*) FROM offline.sync_incidents i" + FILTER, status, severity, type)
                .query(Long.class).single();
        return PageResponse.of(new PageImpl<>(rows, page, total), row -> row);
    }

    public Optional<IncidentResponse> find(UUID id) {
        return jdbc.sql(SELECT + " WHERE i.id = :id").param("id", id).query(IncidentQueries::map).optional();
    }

    public int transition(UUID id, Status from, Status to, UUID actor, String notes) {
        return jdbc.sql("""
                UPDATE offline.sync_incidents
                   SET status = :target, resolution_notes = :notes, resolved_by = :actor,
                       resolved_at = CASE WHEN :target = 'IN_REVIEW' THEN NULL ELSE clock_timestamp() END
                 WHERE id = :id AND status = :previous
                """).param("target", to.name()).param("previous", from.name()).param("notes", notes)
                .param("actor", notes == null ? null : actor).param("id", id).update();
    }

    private JdbcClient.StatementSpec filter(String sql, Status status, Severity severity, Type type) {
        return jdbc.sql(sql)
                .param("status", status == null ? null : status.name(), java.sql.Types.VARCHAR)
                .param("severity", severity == null ? null : severity.name(), java.sql.Types.VARCHAR)
                .param("type", type == null ? null : type.name(), java.sql.Types.VARCHAR);
    }

    private static IncidentResponse map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new IncidentResponse(rs.getObject("id", UUID.class), Status.valueOf(rs.getString("status")),
                Severity.valueOf(rs.getString("severity")), Type.valueOf(rs.getString("incident_type")),
                rs.getBigDecimal("financial_impact_cop"), rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("booking_id", UUID.class), rs.getObject("voucher_id", UUID.class),
                rs.getString("resolution_notes"), rs.getObject("resolved_by", UUID.class),
                rs.getObject("resolved_at", OffsetDateTime.class));
    }
}
