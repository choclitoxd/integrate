package com.integrate.modules.auth.entity;

import com.integrate.common.persistence.PgDomains;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * Acto de consentimiento Habeas Data ({@code auth.habeas_data_consents}, RF-02). Tabla
 * append-only: la aplicación solo inserta (V9) y los triggers bloquean UPDATE/DELETE.
 */
@Entity
@Immutable
@Table(name = "habeas_data_consents", schema = "auth")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HabeasDataConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "policy_id", nullable = false)
    private Short policyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ConsentPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ConsentAction action;

    @Column(name = "ip_address", nullable = false)
    private InetAddress ipAddress;

    @Column(name = "user_agent", nullable = false, length = 1000)
    private String userAgent;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConsentChannel channel;

    /** Gestor o administrador que capturó el consentimiento asistido; {@code null} en autoservicio. */
    @Column(name = "captured_by")
    private UUID capturedBy;

    @Column(name = "physical_consent_form_s3_key", length = 1024, columnDefinition = PgDomains.S3_KEY)
    private String physicalConsentFormS3Key;

    /** Otorgamiento por el propio titular ({@code channel = SELF_SERVICE}, sin {@code captured_by}). */
    public static HabeasDataConsent selfServiceGrant(UUID userId, PrivacyPolicy policy, ConsentPurpose purpose,
                                                     InetAddress ipAddress, String userAgent, Instant now) {
        HabeasDataConsent consent = new HabeasDataConsent();
        consent.userId = userId;
        consent.policyId = policy.getId();
        consent.purpose = purpose;
        consent.action = ConsentAction.GRANTED;
        consent.ipAddress = ipAddress;
        consent.userAgent = userAgent;
        consent.occurredAt = now;
        consent.channel = ConsentChannel.SELF_SERVICE;
        return consent;
    }

    /**
     * Otorgamiento capturado por un gestor en el registro asistido ({@code ASSISTED_PROXY}, ADR-0007 §3).
     * La IP y el user agent son los de la terminal de quien lo captura; el formato físico firmado
     * queda como evidencia en S3.
     */
    public static HabeasDataConsent assistedProxyGrant(UUID userId, PrivacyPolicy policy, ConsentPurpose purpose,
                                                       UUID capturedBy, String physicalConsentFormS3Key,
                                                       InetAddress ipAddress, String userAgent, Instant now) {
        HabeasDataConsent consent = selfServiceGrant(userId, policy, purpose, ipAddress, userAgent, now);
        consent.channel = ConsentChannel.ASSISTED_PROXY;
        consent.capturedBy = capturedBy;
        consent.physicalConsentFormS3Key = physicalConsentFormS3Key;
        return consent;
    }
}
