package com.integrate.modules.auth.service;

import com.integrate.modules.auth.dto.request.ConsentAcceptance;
import com.integrate.modules.auth.entity.RoleCode;
import com.integrate.modules.auth.service.model.ClientInfo;
import com.integrate.modules.auth.service.model.ConsentStatus;
import java.util.Collection;
import java.util.UUID;

/**
 * Consentimiento Habeas Data por autoservicio (RF-02, ADR-0005 §6-§7). Interfaz pública del
 * módulo: el lock de reservas (T-1.4) la consulta para su precondición {@code HEALTH_EMERGENCY}.
 */
public interface ConsentService {

    /** Estado por finalidad aplicable a los roles indicados frente a la política vigente. */
    ConsentStatus status(UUID userId, Collection<RoleCode> roles);

    /** Igual que {@link #status(UUID, Collection)} leyendo los roles vigentes del usuario. */
    ConsentStatus status(UUID userId);

    /**
     * Otorga las finalidades indicadas sobre la política vigente; omite las que ya están vigentes.
     *
     * @throws com.integrate.common.exception.BusinessRuleException {@code VALIDATION_FAILED} (422) si la
     *         versión no es la vigente o alguna finalidad no aplica a los roles del titular
     */
    ConsentStatus grant(UUID userId, ConsentAcceptance acceptance, ClientInfo client);
}
