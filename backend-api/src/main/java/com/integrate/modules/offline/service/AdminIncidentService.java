package com.integrate.modules.offline.service;

import com.integrate.common.audit.AuditEntry;
import com.integrate.common.audit.AuditTrail;
import com.integrate.common.exception.BusinessRuleException;
import com.integrate.common.exception.ErrorCode;
import com.integrate.common.payload.PageResponse;
import com.integrate.modules.offline.repository.IncidentQueries;
import com.integrate.modules.offline.dto.response.IncidentResponse;
import com.integrate.modules.offline.dto.response.IncidentResponse.Severity;
import com.integrate.modules.offline.dto.response.IncidentResponse.Status;
import com.integrate.modules.offline.dto.response.IncidentResponse.Type;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminIncidentService {

    public enum Action {
        TAKEN(Status.OPEN, Status.IN_REVIEW),
        RESOLVED(Status.IN_REVIEW, Status.RESOLVED),
        DISMISSED(Status.IN_REVIEW, Status.DISMISSED);

        private final Status from;
        private final Status to;

        Action(Status from, Status to) {
            this.from = from;
            this.to = to;
        }
    }

    private final IncidentQueries queries;
    private final AuditTrail audit;

    @Transactional(readOnly = true)
    public PageResponse<IncidentResponse> list(Status status, Severity severity, Type type, Pageable page) {
        return queries.list(status, severity, type, page);
    }

    public IncidentResponse detail(UUID id) {
        return queries.find(id).orElseThrow(() -> new BusinessRuleException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Transactional
    public IncidentResponse transition(UUID id, Action action, UUID actor, String notes) {
        if (action != Action.TAKEN && (notes == null || notes.isBlank())) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED);
        }
        int changed = queries.transition(id, action.from, action.to, actor, notes);
        IncidentResponse current = detail(id);
        if (changed == 0) {
            throw new BusinessRuleException(ErrorCode.SYNC_INCIDENT_TRANSITION_CONFLICT)
                    .with("currentStatus", current.status());
        }
        var severity = current.severity() == Severity.HIGH ? AuditEntry.Severity.WARNING : AuditEntry.Severity.INFO;
        audit.record(new AuditEntry("offline", "sync_incidents", id.toString(), "SYNC_INCIDENT_" + action.name(),
                severity, actor, null, null, Map.of("previousStatus", action.from.name(), "status", action.to.name())));
        return current;
    }
}
