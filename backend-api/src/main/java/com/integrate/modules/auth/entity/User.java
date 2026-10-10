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
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

/**
 * Identidad de cualquier actor humano ({@code auth.users}, V2/V10). Los roles viven en
 * {@link UserRole}. {@code created_by}, {@code created_at} y {@code updated_at} los fija
 * PostgreSQL (default {@code common.current_actor()} y trigger), por eso no se escriben.
 */
@Entity
@Table(name = "users", schema = "auth")
@DynamicUpdate
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 254, columnDefinition = PgDomains.EMAIL)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false, length = 3, columnDefinition = PgDomains.DOC_TYPE)
    private DocType docType;

    @Column(name = "doc_number", nullable = false, length = 20, columnDefinition = PgDomains.DOC_NUMBER)
    private String docNumber;

    @Column(nullable = false, length = 16, columnDefinition = PgDomains.PHONE_E164)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "credential_status", nullable = false, length = 20)
    private CredentialStatus credentialStatus;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "anonymized_at", insertable = false, updatable = false)
    private Instant anonymizedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at", insertable = false, updatable = false)
    private Instant deletedAt;

    public static User register(String email, String passwordHash, String fullName, DocType docType,
                                String docNumber, String phone) {
        User user = new User();
        user.email = email;
        user.passwordHash = passwordHash;
        user.fullName = fullName;
        user.docType = docType;
        user.docNumber = docNumber;
        user.phone = phone;
        user.status = UserStatus.ACTIVE;
        user.credentialStatus = CredentialStatus.ACTIVE;
        return user;
    }

    /**
     * Anfitrión registrado por un gestor (ADR-0007 §3): {@code PENDING_RESET} con un hash de un secreto
     * aleatorio que nadie conoce. No puede iniciar sesión hasta activar la cuenta.
     */
    public static User registerPendingReset(String email, String unusablePasswordHash, String fullName,
                                            DocType docType, String docNumber, String phone) {
        User user = register(email, unusablePasswordHash, fullName, docType, docNumber, phone);
        user.credentialStatus = CredentialStatus.PENDING_RESET;
        return user;
    }

    /**
     * Fija la contraseña elegida por el titular, por activación asistida (ADR-0007 §6) o por restablecimiento
     * por correo (ADR-0014 §4): la cuenta queda activa (el titular ya demostró su identidad con el código o el
     * enlace) y sin bloqueo, porque el bloqueo protegía una contraseña que deja de existir.
     */
    public void resetCredentials(String passwordHash) {
        this.passwordHash = passwordHash;
        this.credentialStatus = CredentialStatus.ACTIVE;
        this.lockedUntil = null;
    }

    /** {@code true} si la cuenta no puede autenticarse de ningún modo (anonimizada o dada de baja). */
    public boolean isClosed() {
        return status == UserStatus.ANONYMIZED || deletedAt != null;
    }

    public boolean isLockedAt(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public void lockUntil(Instant until) {
        this.lockedUntil = until;
    }

    public void recordSuccessfulLogin(Instant now) {
        this.lastLoginAt = now;
        this.lockedUntil = null;
    }
}
