package com.integrate.modules.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * Versión publicada de la política de tratamiento de datos ({@code auth.privacy_policies}).
 * Su contenido es inmutable (trigger {@code trg_privacy_policy_freeze}); se publica por migración (ADR-0005 §8).
 */
@Entity
@Immutable
@Table(name = "privacy_policies", schema = "auth")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrivacyPolicy {

    @Id
    private Short id;

    @Column(nullable = false, length = 20)
    private String version;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "document_url", nullable = false, length = 500)
    private String documentUrl;

    @Column(name = "content_sha256", nullable = false, length = 64, columnDefinition = PgDomains.SHA256_HEX)
    private String contentSha256;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "is_current", nullable = false)
    private boolean current;
}
