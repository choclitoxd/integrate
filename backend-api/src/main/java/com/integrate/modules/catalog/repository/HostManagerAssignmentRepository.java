package com.integrate.modules.catalog.repository;

import com.integrate.modules.catalog.entity.HostManagerAssignment;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HostManagerAssignmentRepository extends JpaRepository<HostManagerAssignment, Long> {

    /** Anfitriones bajo la jurisdicción vigente del gestor (índice {@code ix_hma_manager_active}). */
    Page<HostManagerAssignment> findByManagerIdAndValidToIsNull(UUID managerId, Pageable pageable);

    boolean existsByHostIdAndManagerIdAndValidToIsNull(UUID hostId, UUID managerId);
}
