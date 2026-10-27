package com.integrate.modules.catalog.service;

import com.integrate.common.payload.PageResponse;
import com.integrate.modules.catalog.dto.request.ExperienceRequest;
import com.integrate.modules.catalog.dto.request.ScheduleBatchRequest;
import com.integrate.modules.catalog.dto.response.HostExperienceResponse;
import com.integrate.modules.catalog.dto.response.ScheduleResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

/**
 * Autoría del anfitrión sobre sus experiencias y franjas (RF-05, ADR-0006 §4-§5). {@code hostUserId}
 * es siempre el {@code sub} del JWT. Una experiencia o franja de otro anfitrión responde 404.
 * Sin perfil de anfitrión vigente → 403.
 */
public interface HostExperienceService {

    HostExperienceResponse create(UUID hostUserId, ExperienceRequest request);

    PageResponse<HostExperienceResponse> listMine(UUID hostUserId, Pageable pageable);

    HostExperienceResponse getMine(UUID hostUserId, UUID experienceId);

    /** Solo en {@code DRAFT} o {@code REJECTED}; una rechazada vuelve a {@code DRAFT}. */
    HostExperienceResponse update(UUID hostUserId, UUID experienceId, ExperienceRequest request);

    /** {@code DRAFT → PENDING_REVIEW}. */
    HostExperienceResponse submit(UUID hostUserId, UUID experienceId);

    /** Lote atómico; horas locales {@code America/Bogota}. */
    List<ScheduleResponse> createSchedules(UUID hostUserId, UUID experienceId, ScheduleBatchRequest request);

    /** Franjas cuyo inicio local cae en {@code [dateFrom, dateTo]}, cualquier estado. */
    List<ScheduleResponse> listSchedules(UUID hostUserId, UUID experienceId, LocalDate dateFrom, LocalDate dateTo);

    /** Solo franjas futuras sin cupos vendidos (ADR-0006 §5). */
    ScheduleResponse cancelSchedule(UUID hostUserId, UUID scheduleId, String reason);

    /** Repite la verificación del punto de encuentro y devuelve la experiencia con el resultado (ADR-0030). */
    HostExperienceResponse checkMeetingPoint(UUID hostUserId, UUID experienceId);
}
