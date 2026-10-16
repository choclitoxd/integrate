package com.integrate.modules.catalog.repository;

import com.integrate.modules.catalog.entity.ExperienceStop;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExperienceStopRepository extends JpaRepository<ExperienceStop, ExperienceStop.Key> {

    /** Paradas de varias experiencias en orden, para mapear páginas sin N+1. */
    List<ExperienceStop> findByExperienceIdInOrderByExperienceIdAscPositionAsc(Collection<UUID> experienceIds);

    @Modifying(flushAutomatically = true)
    @Query("delete from ExperienceStop s where s.experienceId = :experienceId")
    void deleteByExperienceId(@Param("experienceId") UUID experienceId);
}
