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
import org.hibernate.annotations.DynamicUpdate;

/**
 * Sesión revocable ({@code auth.refresh_tokens}). Solo se persiste el SHA-256 del token opaco;
 * la rotación revoca el anterior con {@code ROTATED} y lo enlaza con {@code replaced_by_id} (ADR-0005 §5).
 */
@Entity
@Table(name = "refresh_tokens", schema = "auth")
@DynamicUpdate
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "token_sha256", nullable = false, updatable = false, length = 64,
            columnDefinition = PgDomains.SHA256_HEX)
    private String tokenSha256;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revoke_reason", length = 30)
    private RevokeReason revokeReason;

    @Column(name = "replaced_by_id")
    private UUID replacedById;

    @Column(name = "ip_address", updatable = false)
    private InetAddress ipAddress;

    @Column(name = "user_agent", updatable = false, length = 1000)
    private String userAgent;

    public static RefreshToken issue(UUID userId, String tokenSha256, Instant now, Instant expiresAt,
                                     InetAddress ipAddress, String userAgent) {
        RefreshToken token = new RefreshToken();
        token.userId = userId;
        token.tokenSha256 = tokenSha256;
        token.issuedAt = now;
        token.expiresAt = expiresAt;
        token.ipAddress = ipAddress;
        token.userAgent = userAgent;
        return token;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpiredAt(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public void revoke(RevokeReason reason, Instant now) {
        this.revokedAt = now;
        this.revokeReason = reason;
    }

    public void rotateTo(UUID successorId, Instant now) {
        revoke(RevokeReason.ROTATED, now);
        this.replacedById = successorId;
    }
}
