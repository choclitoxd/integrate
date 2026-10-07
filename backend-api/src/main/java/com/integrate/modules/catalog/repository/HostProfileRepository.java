package com.integrate.modules.catalog.repository;

import com.integrate.modules.catalog.entity.HostProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HostProfileRepository extends JpaRepository<HostProfile, UUID> {

    /** Perfil vigente del usuario (índice parcial {@code uq_host_profiles_user_active}). */
    Optional<HostProfile> findByUserIdAndDeletedAtIsNull(UUID userId);

    boolean existsByUserIdAndDeletedAtIsNull(UUID userId);
}
