package com.integrate.modules.catalog.service.impl;

import com.integrate.common.audit.AuditEntry;
import com.integrate.common.audit.AuditEntry.Severity;
import com.integrate.common.audit.AuditTrail;
import com.integrate.common.exception.BusinessRuleException;
import com.integrate.common.exception.ErrorCode;
import com.integrate.modules.auth.entity.RoleCode;
import com.integrate.modules.auth.service.RoleAssignmentService;
import com.integrate.modules.catalog.dto.response.HostStatusResponse;
import com.integrate.modules.catalog.entity.AccreditationStatus;
import com.integrate.modules.catalog.entity.HostProfile;
import com.integrate.modules.catalog.repository.HostProfileRepository;
import com.integrate.modules.catalog.service.HostAdminService;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class HostAdminServiceImpl implements HostAdminService {

    private final HostProfileRepository hostProfileRepository;
    private final RoleAssignmentService roleAssignmentService;
    private final AuditTrail auditTrail;

    @Override
    @Transactional
    public HostStatusResponse changeStatus(UUID hostProfileId, AccreditationStatus target, String reason,
                                           UUID adminUserId) {
        HostProfile profile = hostProfileRepository.findById(hostProfileId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> CatalogErrors.notFound("Host profile"));
        AccreditationStatus previous = profile.getStatus();
        if (!profile.canMoveTo(target)) {
            throw new BusinessRuleException(ErrorCode.CATALOG_INVALID_STATUS_TRANSITION,
                    "Cannot move a host from " + previous + " to " + target)
                    .with("currentStatus", previous.name());
        }
        profile.moveTo(target);
        hostProfileRepository.flush();
        HostStatusResponse response = new HostStatusResponse(profile.getId(), target);
        if (target == AccreditationStatus.REVOKED) {
            // ADR-0070 §3: REVOKED es terminal, así que el rol se quita (TOURIST se conserva, ADR-0064).
            // SUSPENDED no: es reversible y el servidor ya niega las operaciones del anfitrión suspendido.
            roleAssignmentService.revoke(profile.getUserId(), RoleCode.HOST, adminUserId);
        }
        // Debe ser lo último de la transacción (encadenamiento de la bitácora).
        auditTrail.record(new AuditEntry("catalog", "host_profiles", profile.getId().toString(), action(target),
                target == AccreditationStatus.ACTIVE ? Severity.INFO : Severity.WARNING, adminUserId, null, null,
                Map.of("userId", profile.getUserId().toString(), "previousStatus", previous.name(),
                        "reason", reason)));
        return response;
    }

    private static String action(AccreditationStatus target) {
        return switch (target) {
            case ACTIVE -> "HOST_REINSTATED";
            case SUSPENDED -> "HOST_SUSPENDED";
            case REVOKED -> "HOST_REVOKED";
        };
    }
}
