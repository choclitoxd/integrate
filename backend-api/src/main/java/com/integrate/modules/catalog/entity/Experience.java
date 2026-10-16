package com.integrate.modules.catalog.entity;

import com.integrate.common.persistence.PgDomains;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Experiencia turística comunitaria ({@code catalog.experiences}). Su estado sigue la máquina de
 * {@link ExperienceStatus}; el trigger {@code fn_experience_guard} la impone también en la BD y
 * {@code fn_experience_status_log} escribe el historial con el actor y el motivo de la transacción.
 * {@code created_by} lo fija PostgreSQL con {@code common.current_actor()} (ADR-0005 §10).
 */
@Entity
@Table(name = "experiences", schema = "catalog")
@DynamicUpdate
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Experience {

    /** Fija para el MVP: el contrato declara {@code timezone} constante (ADR-0006 §4). */
    public static final String TIMEZONE = "America/Bogota";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "host_id", nullable = false, updatable = false)
    private UUID hostId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "safety_guidelines", nullable = false, columnDefinition = "text")
    private String safetyGuidelines;

    @Enumerated(EnumType.STRING)
    @Column(name = "physical_intensity", nullable = false, length = 10)
    private PhysicalIntensity physicalIntensity;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "municipality_code", nullable = false, length = 5)
    private String municipalityCode;

    @Column(length = 150)
    private String vereda;

    @Column(name = "meeting_point", nullable = false, length = 500)
    private String meetingPoint;

    @Column(precision = 8, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "route_type", length = 12)
    private RouteType routeType;

    @Column(name = "distance_km", precision = 5, scale = 2)
    private BigDecimal distanceKm;

    @Column(name = "round_trip_minutes")
    private Integer roundTripMinutes;

    @Column(name = "max_capacity_per_slot", nullable = false)
    private short maxCapacityPerSlot;

    @Column(name = "unit_price_cop", nullable = false, precision = 14, scale = 2, columnDefinition = PgDomains.COP_AMOUNT)
    private BigDecimal unitPriceCop;

    @Column(nullable = false, length = 40)
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExperienceStatus status;

    @Column(name = "rejection_reason", columnDefinition = "text")
    private String rejectionReason;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at", insertable = false, updatable = false)
    private Instant deletedAt;

    public static Experience draft(UUID hostId, ExperienceContent content) {
        Experience experience = new Experience();
        experience.hostId = hostId;
        experience.timezone = TIMEZONE;
        experience.status = ExperienceStatus.DRAFT;
        experience.apply(content);
        return experience;
    }

    /** Reemplaza los campos editables. Una propuesta rechazada vuelve a {@code DRAFT} al editarse. */
    public void edit(ExperienceContent content) {
        apply(content);
        if (status == ExperienceStatus.REJECTED) {
            status = ExperienceStatus.DRAFT;
        }
    }

    public void transitionTo(ExperienceStatus target) {
        this.status = target;
    }

    public void reject(String reason) {
        this.status = ExperienceStatus.REJECTED;
        this.rejectionReason = reason;
    }

    public boolean isPublic() {
        return status == ExperienceStatus.APPROVED && deletedAt == null;
    }

    /** Tarifa plana en COP enteros (dominio {@code common.cop_amount}: sin centavos). */
    public long unitPriceCopValue() {
        return unitPriceCop.longValueExact();
    }

    private void apply(ExperienceContent c) {
        this.title = c.title();
        this.description = c.description();
        this.safetyGuidelines = c.safetyGuidelines();
        this.physicalIntensity = c.physicalIntensity();
        this.durationMinutes = c.durationMinutes();
        this.municipalityCode = c.municipalityCode();
        this.vereda = c.vereda();
        this.meetingPoint = c.meetingPoint();
        this.latitude = c.latitude();
        this.longitude = c.longitude();
        this.routeType = c.routeType();
        this.distanceKm = c.distanceKm();
        this.roundTripMinutes = c.roundTripMinutes();
        this.maxCapacityPerSlot = (short) c.maxCapacityPerSlot();
        this.unitPriceCop = BigDecimal.valueOf(c.unitPriceCop());
    }

    /** Campos que el anfitrión define (ADR-0006 §4). */
    public record ExperienceContent(String title, String description, String safetyGuidelines,
                                    PhysicalIntensity physicalIntensity, int durationMinutes, String municipalityCode,
                                    String vereda, String meetingPoint, BigDecimal latitude, BigDecimal longitude,
                                    RouteType routeType, BigDecimal distanceKm, Integer roundTripMinutes,
                                    int maxCapacityPerSlot, long unitPriceCop) {
    }
}
