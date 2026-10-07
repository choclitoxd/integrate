package com.integrate.modules.catalog.repository;

import com.integrate.modules.catalog.entity.LinkManagerProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LinkManagerProfileRepository extends JpaRepository<LinkManagerProfile, UUID> {

    /** Acreditación vigente del usuario (índice parcial {@code uq_link_manager_user_active}). */
    Optional<LinkManagerProfile> findByUserIdAndDeletedAtIsNull(UUID userId);

    boolean existsByUserIdAndDeletedAtIsNull(UUID userId);
}
