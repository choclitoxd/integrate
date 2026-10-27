package com.integrate.modules.catalog.service;

import com.integrate.common.payload.PageResponse;
import com.integrate.modules.catalog.dto.response.HostExperienceResponse;
import com.integrate.modules.catalog.entity.ExperienceStatus;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

/**
 * Moderación del catálogo por el administrador (RF-07, RF-08, ADR-0006 §4). Cada transición queda en
 * {@code catalog.experience_status_history} con el actor y el motivo. Una transición no permitida → 409.
 */
public interface CatalogModerationService {

    /** {@code status} nulo = todas. La cola de revisión es {@code PENDING_REVIEW}. */
    PageResponse<HostExperienceResponse> list(ExperienceStatus status, Pageable pageable);

    HostExperienceResponse approve(UUID experienceId);

    HostExperienceResponse reject(UUID experienceId, String reason);

    /** Además cierra las franjas futuras abiertas. */
    HostExperienceResponse suspend(UUID experienceId, String reason);

    /** Además reabre las franjas futuras que la suspensión cerró. */
    HostExperienceResponse reinstate(UUID experienceId);

    /** Además cierra las franjas futuras abiertas. */
    HostExperienceResponse retire(UUID experienceId, String reason);

    /** El administrador acepta un punto de encuentro dudoso (ADR-0030 §4). */
    HostExperienceResponse confirmMeetingPoint(UUID experienceId);
}
