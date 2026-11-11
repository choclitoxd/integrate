package com.integrate.modules.catalog.service;

import com.integrate.common.payload.PageResponse;
import com.integrate.modules.catalog.dto.request.ExperienceRequest;
import com.integrate.modules.catalog.dto.response.HostExperienceResponse;
import com.integrate.modules.catalog.entity.ExperienceStatus;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

/**
 * Moderación del catálogo por el administrador (RF-07, RF-08, ADR-0006 §4, ADR-0047 §1). Cada transición queda en
 * {@code catalog.experience_status_history} con el actor y el motivo. Una transición no permitida → 409.
 */
public interface CatalogModerationService {

    /** {@code status} nulo = todas. La cola de revisión es {@code PENDING_REVIEW}. */
    PageResponse<HostExperienceResponse> list(ExperienceStatus status, Pageable pageable);

    /**
     * Crea una experiencia a nombre de un anfitrión y la deja en revisión (ADR-0048 §2).
     *
     * @throws com.integrate.common.exception.BusinessRuleException {@code RESOURCE_NOT_FOUND} (404) si el perfil de
     *         anfitrión no existe; {@code VALIDATION_FAILED} (422)
     */
    HostExperienceResponse create(UUID hostProfileId, ExperienceRequest request, UUID adminUserId);

    /** Actualiza los datos de una experiencia sin alterar su estado de publicación (ADR-0047 §1). */
    HostExperienceResponse update(UUID experienceId, ExperienceRequest request, UUID adminUserId);

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
