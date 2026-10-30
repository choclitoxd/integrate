package com.integrate.common.audit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link SensitiveAccessRecorder} sobre {@link AuditTrail}: una sola fila {@code SENSITIVE_DATA_ACCESSED} por acceso,
 * con la lista de titulares en {@code details.subjects} (la consulta {@code details @> …} la atiende el índice GIN).
 * Una fila por titular saturaría la cabeza de la cadena en una descarga grande.
 */
@Component
@RequiredArgsConstructor
class AuditSensitiveAccessRecorder implements SensitiveAccessRecorder {

    static final String ACTION = "SENSITIVE_DATA_ACCESSED";
    /** Tope de titulares guardados por fila; si se supera se marca {@code subjectsTruncated}. */
    static final int MAX_SUBJECTS = 5_000;

    private final AuditTrail auditTrail;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Access access) {
        List<String> subjects = access.subjects().stream().distinct().sorted().toList();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("purpose", access.purpose());
        details.put("fields", access.fields().stream().distinct().sorted().toList());
        details.put("subjectCount", subjects.size());
        details.put("subjects", subjects.size() > MAX_SUBJECTS ? subjects.subList(0, MAX_SUBJECTS) : subjects);
        if (subjects.size() > MAX_SUBJECTS) {
            details.put("subjectsTruncated", true);
        }
        auditTrail.record(new AuditEntry(access.module(), access.entityName(), access.entityId(), ACTION,
                AuditEntry.Severity.INFO, access.actorId(), access.actorIp(), access.userAgent(), details));
    }
}