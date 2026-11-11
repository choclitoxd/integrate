package com.integrate.modules.catalog.repository;

import com.integrate.modules.catalog.entity.HostManagerAssignment;
import com.integrate.modules.auth.entity.CredentialStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface HostManagerAssignmentRepository extends JpaRepository<HostManagerAssignment, Long> {

    /** Anfitriones bajo la jurisdicción vigente del gestor (índice {@code ix_hma_manager_active}). */
    @Query("""
            select a from HostManagerAssignment a
            join HostProfile h on h.id = a.hostId
            join User u on u.id = h.userId
            where a.managerId = :managerId and a.validTo is null
              and (:name = '' or locate(lower(:name), lower(u.fullName)) > 0)
              and (:activation is null or u.credentialStatus = :activation)
            """)
    Page<HostManagerAssignment> findManagedHosts(UUID managerId, String name, CredentialStatus activation,
                                                  Pageable pageable);

    boolean existsByHostIdAndManagerIdAndValidToIsNull(UUID hostId, UUID managerId);
}
