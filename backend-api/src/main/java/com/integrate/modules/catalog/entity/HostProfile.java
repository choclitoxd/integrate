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
 * Perfil de anfitrión ({@code catalog.host_profiles}). Las columnas cifradas de condición de víctima
 * ({@code *_enc}, {@code support_doc_s3_key}, {@code sensitive_key_version}) <b>no se mapean</b> a
 * propósito: llegan con el registro asistido (T-3.5) y AES-GCM (T-4.1), y mientras tanto ningún
 * DTO puede filtrarlas por accidente (R-05, ADR-0006 §3).
 */
@Entity
@Table(name = "host_profiles", schema = "catalog")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HostProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "association_id", nullable = false, updatable = false)
    private UUID associationId;

    @Column(name = "is_verified", nullable = false)
    private boolean verified;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at", insertable = false, updatable = false)
    private Instant deletedAt;

    public static HostProfile create(UUID userId, UUID associationId) {
        HostProfile profile = new HostProfile();
        profile.userId = userId;
        profile.associationId = associationId;
        return profile;
    }
}
