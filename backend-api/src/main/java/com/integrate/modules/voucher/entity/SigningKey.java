package com.integrate.modules.voucher.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

/**
 * Llave pública de verificación de vouchers ({@code voucher.signing_keys}, G2). La privada vive solo en el backend
 * ({@code VOUCHER_SIGNING_KEY}); aquí se publica su PEM SPKI para la PWA (RF-16).
 */
@Entity
@Table(name = "signing_keys", schema = "voucher")
@DynamicUpdate
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SigningKey {

    public static final String ACTIVE = "ACTIVE";
    public static final String RETIRED = "RETIRED";
    public static final String RS256 = "RS256";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Short id;

    @Column(nullable = false, length = 64, updatable = false)
    private String kid;

    @Column(nullable = false, length = 10, updatable = false)
    private String algorithm;

    @Column(name = "public_key_pem", nullable = false, updatable = false, columnDefinition = "text")
    private String publicKeyPem;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "valid_from", nullable = false, updatable = false)
    private Instant validFrom;

    @Column(name = "retired_at")
    private Instant retiredAt;

    public static SigningKey activate(String kid, String publicKeyPem, Instant now) {
        SigningKey key = new SigningKey();
        key.kid = kid;
        key.algorithm = RS256;
        key.publicKeyPem = publicKeyPem;
        key.status = ACTIVE;
        key.validFrom = now;
        return key;
    }

    /** La llave deja de firmar; sigue sirviendo para verificar vouchers ya emitidos. */
    public void retire(Instant now) {
        this.status = RETIRED;
        this.retiredAt = now;
    }

    public boolean isActive() {
        return ACTIVE.equals(status);
    }
}
