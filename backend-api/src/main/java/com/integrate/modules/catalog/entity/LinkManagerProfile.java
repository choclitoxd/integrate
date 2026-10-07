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
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

/**
 * Acreditación de un gestor de enlace ante una asociación ({@code catalog.link_manager_profiles}, V3,
 * ADR-0007 §2). Solo un gestor {@code ACTIVE} enrola anfitriones y opera en {@code /api/v1/gestor/**}.
 */
@Entity
@Table(name = "link_manager_profiles", schema = "catalog")
@DynamicUpdate
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LinkManagerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "association_id", nullable = false, updatable = false)
    private UUID associationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "accreditation_status", nullable = false, length = 20)
    private AccreditationStatus accreditationStatus;

    @Column(name = "accredited_on", nullable = false, updatable = false)
    private LocalDate accreditedOn;

    @Column(name = "accredited_by", updatable = false)
    private UUID accreditedBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at", insertable = false, updatable = false)
    private Instant deletedAt;

    public static LinkManagerProfile accredit(UUID userId, UUID associationId, LocalDate accreditedOn,
                                              UUID accreditedBy) {
        LinkManagerProfile profile = new LinkManagerProfile();
        profile.userId = userId;
        profile.associationId = associationId;
        profile.accreditationStatus = AccreditationStatus.ACTIVE;
        profile.accreditedOn = accreditedOn;
        profile.accreditedBy = accreditedBy;
        return profile;
    }

    public boolean isActive() {
        return accreditationStatus == AccreditationStatus.ACTIVE && deletedAt == null;
    }

    /** {@code true} si la transición está permitida; {@code REVOKED} es terminal. */
    public boolean canMoveTo(AccreditationStatus target) {
        return switch (accreditationStatus) {
            case ACTIVE -> target == AccreditationStatus.SUSPENDED || target == AccreditationStatus.REVOKED;
            case SUSPENDED -> target == AccreditationStatus.ACTIVE || target == AccreditationStatus.REVOKED;
            case REVOKED -> false;
        };
    }

    /** El invocador valida antes con {@link #canMoveTo(AccreditationStatus)}. */
    public void moveTo(AccreditationStatus target) {
        if (!canMoveTo(target)) {
            throw new IllegalStateException("Invalid accreditation transition " + accreditationStatus + " -> " + target);
        }
        this.accreditationStatus = target;
    }
}
