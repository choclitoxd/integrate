package com.integrate.modules.audit.repository;

import com.integrate.modules.audit.dto.response.AuditRecordResponse;
import com.integrate.modules.audit.service.model.AuditFilter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lectura de {@code audit.audit_trail} (ADR-0035 §1). Solo {@code SELECT} y la función de verificación: este módulo
 * nunca escribe en la tabla (la escritura es de {@code AuditTrail}).
 */
@Repository
@RequiredArgsConstructor
public class AuditQueries {

    private static final String COLUMNS = """
            SELECT id, created_at, module, entity_name, entity_id, action, severity, actor_id,
                   host(actor_ip) AS actor_ip, user_agent, details::text AS details
              FROM audit.audit_trail""";
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public record BrokenLink(long brokenId, String problem) {
    }

    public long count(AuditFilter filter) {
        Where where = where(filter);
        return jdbc.sql("SELECT count(*) FROM audit.audit_trail" + where.sql).params(where.params)
                .query(Long.class).single();
    }

    public List<AuditRecordResponse> search(AuditFilter filter, int limit, long offset) {
        Where where = where(filter);
        Map<String, Object> params = new HashMap<>(where.params);
        params.put("limit", limit);
        params.put("offset", offset);
        return jdbc.sql(COLUMNS + where.sql + " ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset")
                .params(params).query(this::record).list();
    }

    public Optional<Long> maxId() {
        return jdbc.sql("SELECT max(id) FROM audit.audit_trail").query((rs, n) -> {
            long value = rs.getLong(1);
            return rs.wasNull() ? null : value;
        }).optional();
    }

    /** Primera fila rota del rango según {@code audit.verify_chain}; vacío si la cadena es íntegra. */
    public Optional<BrokenLink> verifyChain(long fromId, long toId) {
        return jdbc.sql("SELECT broken_id, problem FROM audit.verify_chain(:from, :to)")
                .param("from", fromId).param("to", toId)
                .query((rs, n) -> new BrokenLink(rs.getLong("broken_id"), rs.getString("problem"))).optional();
    }

    private AuditRecordResponse record(ResultSet rs, int row) throws SQLException {
        OffsetDateTime created = rs.getObject("created_at", OffsetDateTime.class);
        return new AuditRecordResponse(rs.getLong("id"), created.toInstant(), rs.getString("module"),
                rs.getString("entity_name"), rs.getString("entity_id"), rs.getString("action"),
                rs.getString("severity"), rs.getObject("actor_id", UUID.class), rs.getString("actor_ip"),
                rs.getString("user_agent"), json.readValue(rs.getString("details"), MAP));
    }

    private record Where(String sql, Map<String, Object> params) {
    }

    private Where where(AuditFilter f) {
        StringBuilder sql = new StringBuilder(" WHERE 1 = 1");
        Map<String, Object> params = new HashMap<>();
        eq(sql, params, "module", f.module());
        eq(sql, params, "action", f.action());
        eq(sql, params, "entity_name", f.entityName());
        eq(sql, params, "entity_id", f.entityId());
        eq(sql, params, "severity", f.severity());
        if (f.actorId() != null) {
            sql.append(" AND actor_id = :actorId");
            params.put("actorId", f.actorId());
        }
        if (f.from() != null) {
            sql.append(" AND created_at >= :from");
            params.put("from", f.from().atOffset(ZoneOffset.UTC));
        }
        if (f.to() != null) {
            sql.append(" AND created_at < :to");
            params.put("to", f.to().atOffset(ZoneOffset.UTC));
        }
        if (f.subject() != null) {
            // Contención jsonb atendida por el índice GIN de details; el JSON se construye, no se concatena.
            sql.append(" AND details @> CAST(:subject AS jsonb)");
            params.put("subject", json.writeValueAsString(Map.of("subjects", List.of(f.subject()))));
        }
        return new Where(sql.toString(), params);
    }

    private static void eq(StringBuilder sql, Map<String, Object> params, String column, String value) {
        if (value != null) {
            sql.append(" AND ").append(column).append(" = :").append(column);
            params.put(column, value);
        }
    }
}