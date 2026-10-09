package com.integrate.modules.catalog.repository;

import com.integrate.modules.catalog.entity.Schedule;
import com.integrate.modules.catalog.entity.ScheduleStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    /** Bloqueo pesimista de la franja para el motor de reservas (RF-09, ADR-0008 §2). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Schedule s where s.id = :id")
    Optional<Schedule> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Descuenta cupos de una franja ya bloqueada. Nativo para no reescribir columnas ni disparar el trigger de
     * capacidad; la condición {@code available_slots >= :slots} es una defensa adicional al {@code CHECK}.
     *
     * @return 1 si se descontó, 0 si no había cupos suficientes
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE catalog.schedules SET available_slots = available_slots - :slots
             WHERE id = :id AND available_slots >= :slots""", nativeQuery = true)
    int decrementAvailableSlots(@Param("id") UUID id, @Param("slots") int slots);

    /** Restituye cupos; el {@code CHECK available_slots <= total_slots} impide devolver de más. */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE catalog.schedules SET available_slots = available_slots + :slots WHERE id = :id",
            nativeQuery = true)
    int incrementAvailableSlots(@Param("id") UUID id, @Param("slots") int slots);

    /** Suspensión, reactivación y retiro (RF-08): mueve solo las franjas futuras en el estado {@code from}. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Schedule s set s.status = :to
             where s.experienceId = :experienceId and s.status = :from and s.startTime > :now""")
    int moveFutureSchedules(@Param("experienceId") UUID experienceId, @Param("from") ScheduleStatus from,
                            @Param("to") ScheduleStatus to, @Param("now") Instant now);
}
