package com.integrate.modules.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

/**
 * Franja horaria con aforo ({@code catalog.schedules}). Es la fila que el motor de reservas (T-1.4)
 * bloquea con {@code SELECT … FOR UPDATE}; este módulo nunca modifica {@code available_slots}.
 */
@Entity
@Table(name = "schedules", schema = "catalog")
@DynamicUpdate
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Schedule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "experience_id", nullable = false, updatable = false)
    private UUID experienceId;

    @Column(name = "start_time", nullable = false, updatable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false, updatable = false)
    private Instant endTime;

    @Column(name = "total_slots", nullable = false, updatable = false)
    private short totalSlots;

    @Column(name = "available_slots", nullable = false, updatable = false)
    private short availableSlots;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScheduleStatus status;

    @Column(name = "cancellation_reason", columnDefinition = "text")
    private String cancellationReason;

    public static Schedule open(UUID experienceId, Instant start, Instant end, int totalSlots) {
        Schedule schedule = new Schedule();
        schedule.experienceId = experienceId;
        schedule.startTime = start;
        schedule.endTime = end;
        schedule.totalSlots = (short) totalSlots;
        schedule.availableSlots = (short) totalSlots;
        schedule.status = ScheduleStatus.OPEN;
        return schedule;
    }

    /** Hay cupos vendidos o retenidos por reservas. */
    public boolean hasBookings() {
        return availableSlots < totalSlots;
    }

    public void cancel(String reason) {
        this.status = ScheduleStatus.CANCELLED;
        this.cancellationReason = reason;
    }
}
