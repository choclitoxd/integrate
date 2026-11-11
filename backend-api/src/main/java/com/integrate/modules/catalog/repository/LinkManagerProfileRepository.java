package com.integrate.modules.catalog.repository;

import com.integrate.modules.catalog.entity.AccreditationStatus;
import com.integrate.modules.catalog.entity.LinkManagerProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LinkManagerProfileRepository extends JpaRepository<LinkManagerProfile, UUID> {

    /** Acreditación vigente del usuario (índice parcial {@code uq_link_manager_user_active}). */
    Optional<LinkManagerProfile> findByUserIdAndDeletedAtIsNull(UUID userId);

    boolean existsByUserIdAndDeletedAtIsNull(UUID userId);

    /** Listado del administrador (ADR-0043 §1.3), con o sin filtro por estado de acreditación. */
    Page<LinkManagerProfile> findByDeletedAtIsNull(Pageable pageable);

    Page<LinkManagerProfile> findByAccreditationStatusAndDeletedAtIsNull(AccreditationStatus status,
                                                                         Pageable pageable);
}
