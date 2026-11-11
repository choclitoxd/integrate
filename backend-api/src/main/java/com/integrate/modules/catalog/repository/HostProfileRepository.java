package com.integrate.modules.catalog.repository;

import com.integrate.modules.catalog.entity.HostProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HostProfileRepository extends JpaRepository<HostProfile, UUID> {

    /** Perfil vigente del usuario (índice parcial {@code uq_host_profiles_user_active}). */
    Optional<HostProfile> findByUserIdAndDeletedAtIsNull(UUID userId);

    boolean existsByUserIdAndDeletedAtIsNull(UUID userId);

    /** Listado del administrador (ADR-0043 §1.4), con o sin filtro por asociación. */
    Page<HostProfile> findByDeletedAtIsNull(Pageable pageable);

    Page<HostProfile> findByAssociationIdAndDeletedAtIsNull(UUID associationId, Pageable pageable);

    /** Un documento soporte respalda a un solo anfitrión (ADR-0007 §5). */
    boolean existsBySupportDocS3Key(String supportDocS3Key);
}
