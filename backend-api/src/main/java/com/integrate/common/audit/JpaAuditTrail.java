package com.integrate.common.audit;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link AuditTrail} con un {@code INSERT} nativo por el {@link EntityManager} de la transacción en curso:
 * Hibernate vacía antes las escrituras pendientes, así que la auditoría queda de verdad al final.
 * {@code integrate_app} tiene {@code INSERT} sobre la tabla (V9) y el trigger es {@code SECURITY DEFINER}.
 */
@Repository
@RequiredArgsConstructor
class JpaAuditTrail implements AuditTrail {

    private static final String INSERT_SQL = """
            INSERT INTO audit.audit_trail
                   (module, entity_name, entity_id, action, severity, actor_id, actor_ip, user_agent, details)
            VALUES (:module, :entityName, :entityId, :action, :severity, CAST(:actorId AS uuid),
                    CAST(:actorIp AS inet), :userAgent, CAST(:details AS jsonb))""";

    private static final int MAX_USER_AGENT = 1000;

    @PersistenceContext
    private EntityManager entityManager;

    private final JsonMapper jsonMapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditEntry entry) {
        entityManager.createNativeQuery(INSERT_SQL)
                .setParameter("module", entry.module())
                .setParameter("entityName", entry.entityName())
                .setParameter("entityId", entry.entityId())
                .setParameter("action", entry.action())
                .setParameter("severity", entry.severity().name())
                .setParameter("actorId", entry.actorId() == null ? null : entry.actorId().toString())
                .setParameter("actorIp", entry.actorIp() == null ? null : entry.actorIp().getHostAddress())
                .setParameter("userAgent", truncate(entry.userAgent()))
                .setParameter("details", jsonMapper.writeValueAsString(entry.details()))
                .executeUpdate();
    }

    private static String truncate(String userAgent) {
        return userAgent == null || userAgent.length() <= MAX_USER_AGENT ? userAgent
                : userAgent.substring(0, MAX_USER_AGENT);
    }
}
