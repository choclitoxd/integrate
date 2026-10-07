package com.integrate.modules.catalog.repository;

import com.integrate.modules.catalog.entity.HostProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HostProfileRepository extends JpaRepository<HostProfile, UUID> {

    /** Perfil vigente del usuario (índice parcial {@code uq_host_profiles_user_active}). */
    Optional<HostProfile> findByUserIdAndDeletedAtIsNull(UUID userId);

    boolean existsByUserIdAndDeletedAtIsNull(UUID userId);

    /** Un documento soporte respalda a un solo anfitrión (ADR-0007 §5). */
    boolean existsBySupportDocS3Key(String supportDocS3Key);
}
