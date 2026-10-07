package com.integrate.modules.catalog.service.impl;

import com.integrate.modules.catalog.entity.LinkManagerProfile;
import com.integrate.modules.catalog.repository.LinkManagerProfileRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolución del gestor que actúa (ADR-0007 §2): el rol del JWT no basta, hace falta una acreditación
 * {@code ACTIVE}. Sin transacción propia.
 */
@Component
@RequiredArgsConstructor
class LinkManagers {

    private final LinkManagerProfileRepository repository;

    /** @throws com.integrate.common.exception.BusinessRuleException {@code ENROLLMENT_MANAGER_NOT_ACTIVE} (403) */
    LinkManagerProfile requireActive(UUID userId) {
        return repository.findByUserIdAndDeletedAtIsNull(userId)
                .filter(LinkManagerProfile::isActive)
                .orElseThrow(CatalogErrors::managerNotActive);
    }
}
