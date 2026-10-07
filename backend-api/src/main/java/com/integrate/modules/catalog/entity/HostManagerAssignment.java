package com.integrate.modules.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Jurisdicción vigente o histórica de un gestor sobre un anfitrión ({@code catalog.host_manager_assignments},
 * V3). Las FK compuestas imponen la misma asociación (RF-03) y {@code EXCLUDE} impide periodos solapados.
 * Vigente = {@code validTo == null}.
 */
@Entity
@Table(name = "host_manager_assignments", schema = "catalog")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HostManagerAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "host_id", nullable = false, updatable = false)
    private UUID hostId;

    @Column(name = "manager_id", nullable = false, updatable = false)
    private UUID managerId;

    @Column(name = "association_id", nullable = false, updatable = false)
    private UUID associationId;

    @Column(name = "valid_from", nullable = false, updatable = false)
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    public static HostManagerAssignment start(UUID hostId, UUID managerId, UUID associationId, Instant now) {
        HostManagerAssignment assignment = new HostManagerAssignment();
        assignment.hostId = hostId;
        assignment.managerId = managerId;
        assignment.associationId = associationId;
        assignment.validFrom = now;
        return assignment;
    }
}
