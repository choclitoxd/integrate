package com.integrate.modules.auth.entity;

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

/** Intento de autenticación ({@code auth.login_attempts}): base del bloqueo 5 fallos / 10 min (RF-01). */
@Entity
@Immutable
@Table(name = "login_attempts", schema = "auth")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "email_attempted", nullable = false, length = 254)
    private String emailAttempted;

    @Column(name = "ip_address", nullable = false)
    private InetAddress ipAddress;

    @Column(name = "user_agent", length = 1000)
    private String userAgent;

    @Column(nullable = false)
    private boolean succeeded;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 30)
    private LoginFailureReason failureReason;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    public static LoginAttempt success(UUID userId, String email, InetAddress ip, String userAgent, Instant now) {
        return of(userId, email, ip, userAgent, true, null, now);
    }

    public static LoginAttempt failure(UUID userId, String email, InetAddress ip, String userAgent,
                                       LoginFailureReason reason, Instant now) {
        return of(userId, email, ip, userAgent, false, reason, now);
    }

    private static LoginAttempt of(UUID userId, String email, InetAddress ip, String userAgent, boolean succeeded,
                                   LoginFailureReason reason, Instant now) {
        LoginAttempt attempt = new LoginAttempt();
        attempt.userId = userId;
        attempt.emailAttempted = email;
        attempt.ipAddress = ip;
        attempt.userAgent = userAgent;
        attempt.succeeded = succeeded;
        attempt.failureReason = reason;
        attempt.attemptedAt = now;
        return attempt;
    }
}
