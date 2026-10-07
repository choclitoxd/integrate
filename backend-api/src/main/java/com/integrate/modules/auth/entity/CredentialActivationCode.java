package com.integrate.modules.auth.entity;

import com.integrate.common.persistence.PgDomains;
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
import org.hibernate.annotations.DynamicUpdate;

/**
 * Código de un solo uso para activar una cuenta {@code PENDING_RESET} ({@code auth.credential_activation_codes},
 * V12, ADR-0007 §6). Solo se guarda el SHA-256 del código normalizado.
 */
@Entity
@Table(name = "credential_activation_codes", schema = "auth")
@DynamicUpdate
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CredentialActivationCode {

    /** Intentos fallidos que invalidan el código ({@code ck_activation_codes_attempts}). */
    public static final int MAX_FAILED_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "code_sha256", nullable = false, updatable = false, length = 64,
            columnDefinition = PgDomains.SHA256_HEX)
    private String codeSha256;

    @Column(name = "issued_by", nullable = false, updatable = false)
    private UUID issuedBy;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "failed_attempts", nullable = false)
    private short failedAttempts;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public static CredentialActivationCode issue(UUID userId, String codeSha256, UUID issuedBy, Instant now,
                                                 Instant expiresAt) {
        CredentialActivationCode code = new CredentialActivationCode();
        code.userId = userId;
        code.codeSha256 = codeSha256;
        code.issuedBy = issuedBy;
        code.issuedAt = now;
        code.expiresAt = expiresAt;
        return code;
    }

    /** Vivo y dentro de plazo: el único estado en que se puede canjear. */
    public boolean isUsableAt(Instant now) {
        return consumedAt == null && revokedAt == null && expiresAt.isAfter(now);
    }

    public void consume(Instant now) {
        this.consumedAt = now;
    }

    public void revoke(Instant now) {
        this.revokedAt = now;
    }

    /** Cuenta un intento fallido; al llegar al máximo el código queda revocado. */
    public void recordFailedAttempt(Instant now) {
        failedAttempts++;
        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            revoke(now);
        }
    }
}
