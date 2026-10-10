package com.integrate.modules.auth.entity;

import com.integrate.common.persistence.PgDomains;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.DynamicUpdate;

/**
 * Token de un solo uso para restablecer la contraseña ({@code auth.password_reset_tokens}, V13, ADR-0014 §2).
 * Solo se guarda el SHA-256 del token opaco que viaja en el correo.
 */
@Entity
@Table(name = "password_reset_tokens", schema = "auth")
@DynamicUpdate
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "token_sha256", nullable = false, updatable = false, length = 64,
            columnDefinition = PgDomains.SHA256_HEX)
    private String tokenSha256;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "ip_address", nullable = false, updatable = false)
    private InetAddress ipAddress;

    @Column(name = "user_agent", nullable = false, updatable = false, length = 1000)
    private String userAgent;

    public static PasswordResetToken issue(UUID userId, String tokenSha256, Instant now, Instant expiresAt,
                                           InetAddress ipAddress, String userAgent) {
        PasswordResetToken token = new PasswordResetToken();
        token.userId = userId;
        token.tokenSha256 = tokenSha256;
        token.requestedAt = now;
        token.expiresAt = expiresAt;
        token.ipAddress = ipAddress;
        token.userAgent = userAgent;
        return token;
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
}
