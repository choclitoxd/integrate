package com.integrate.modules.auth.entity;

import com.integrate.common.persistence.PgDomains;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
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

    @Transient
    private String docNumber;

    @Transient
    private String phone;

    @Column(name = "doc_number_enc", columnDefinition = PgDomains.CIPHERTEXT)
    private byte[] encryptedDocument;

    @Column(name = "doc_key_version")
    private Short documentKeyVersion;

    @Column(name = "document_index")
    private byte[] documentIndex;

    @Column(name = "document_index_version")
    private Short documentIndexVersion;

    @Column(name = "document_index_next")
    private byte[] nextDocumentIndex;

    @Column(name = "document_index_next_version")
    private Short nextDocumentIndexVersion;

    @Column(name = "phone_enc", columnDefinition = PgDomains.CIPHERTEXT)
    private byte[] encryptedPhone;

    @Column(name = "phone_key_version")
    private Short phoneKeyVersion;

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
        user.id = UUID.randomUUID();
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

    public void protectDocument(byte[] encrypted, short keyVersion, byte[] index, short indexVersion) {
        this.encryptedDocument = encrypted;
        this.documentKeyVersion = keyVersion;
        this.documentIndex = index;
        this.documentIndexVersion = indexVersion;
    }

    public void protectPhone(byte[] encrypted, short keyVersion) {
        this.encryptedPhone = encrypted;
        this.phoneKeyVersion = keyVersion;
    }

    public void prepareNextDocumentIndex(byte[] index, short indexVersion) {
        this.nextDocumentIndex = index;
        this.nextDocumentIndexVersion = indexVersion;
    }

    public void updateFullName(String fullName) {
        this.fullName = fullName;
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

    public static User identityForAnonymization(UUID id) {
        User user = new User();
        user.id = id;
        user.docType = DocType.PA;
        user.docNumber = "ANON" + id.toString().replace("-", "").substring(0, 16);
        user.phone = "+10000000000";
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
