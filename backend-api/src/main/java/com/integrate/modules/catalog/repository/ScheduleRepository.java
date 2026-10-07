package com.integrate.modules.catalog.repository;

import com.integrate.modules.catalog.entity.Schedule;
import com.integrate.modules.catalog.entity.ScheduleStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduleRepository extends JpaRepository<Schedule, UUID> {

    /** Franjas en {@code [from, to)} (UTC), cualquier estado, ordenadas por inicio. */
    List<Schedule> findByExperienceIdAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeAsc(
            UUID experienceId, Instant from, Instant to);

    /** Disponibilidad pública: solo {@code OPEN}, futuras y con cupos mínimos (contrato §4). */
    @Query("""
            select s from Schedule s
             where s.experienceId = :experienceId and s.status = com.integrate.modules.catalog.entity.ScheduleStatus.OPEN
               and s.startTime > :now and s.startTime >= :from and s.startTime < :to
               and s.availableSlots >= :minSlots
             order by s.startTime""")
    List<Schedule> findBookable(@Param("experienceId") UUID experienceId, @Param("now") Instant now,
                                @Param("from") Instant from, @Param("to") Instant to,
                                @Param("minSlots") short minSlots);

    boolean existsByExperienceIdAndStartTimeIn(UUID experienceId, Collection<Instant> starts);

    @Query("""
            select coalesce(max(s.totalSlots), 0) from Schedule s
             where s.experienceId = :experienceId and s.status = com.integrate.modules.catalog.entity.ScheduleStatus.OPEN
               and s.startTime > :now""")
    short findMaxOpenFutureSlots(@Param("experienceId") UUID experienceId, @Param("now") Instant now);

    /** Suspensión, reactivación y retiro (RF-08): mueve solo las franjas futuras en el estado {@code from}. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Schedule s set s.status = :to
             where s.experienceId = :experienceId and s.status = :from and s.startTime > :now""")
    int moveFutureSchedules(@Param("experienceId") UUID experienceId, @Param("from") ScheduleStatus from,
                            @Param("to") ScheduleStatus to, @Param("now") Instant now);
}
