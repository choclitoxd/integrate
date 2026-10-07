package com.integrate.modules.catalog.repository;

import com.integrate.modules.catalog.entity.Experience;
import com.integrate.modules.catalog.entity.ExperienceStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExperienceRepository extends JpaRepository<Experience, UUID>, JpaSpecificationExecutor<Experience> {

    Page<Experience> findByHostIdAndDeletedAtIsNull(UUID hostId, Pageable pageable);

    Page<Experience> findByStatusAndDeletedAtIsNull(ExperienceStatus status, Pageable pageable);

    Page<Experience> findByDeletedAtIsNull(Pageable pageable);

    /** Serializa transiciones de estado y ediciones concurrentes de la misma experiencia. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Experience e where e.id = :id and e.deletedAt is null")
    Optional<Experience> findActiveByIdForUpdate(@Param("id") UUID id);
}
