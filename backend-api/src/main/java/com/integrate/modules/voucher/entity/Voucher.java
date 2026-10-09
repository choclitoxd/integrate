package com.integrate.modules.voucher.entity;

import com.integrate.common.persistence.PgDomains;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

/**
 * Voucher de una reserva confirmada ({@code voucher.vouchers}, ADR-0010). El JWT no se guarda (G2): solo su hash.
 * {@code createdAt} se guarda truncado al segundo porque es el claim {@code iat} y debe permitir re-derivar el token.
 */
@Entity
@Table(name = "vouchers", schema = "voucher")
@DynamicUpdate
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Voucher {

    public static final String ISSUED = "ISSUED";

    @Id
    private UUID id;

    @Column(name = "booking_id", nullable = false, updatable = false)
    private UUID bookingId;

    @Column(nullable = false, updatable = false)
    private UUID jti;

    @Column(name = "signing_key_id", nullable = false, updatable = false)
    private short signingKeyId;

    @Column(name = "token_sha256", nullable = false, length = 64, updatable = false,
            columnDefinition = PgDomains.SHA256_HEX)
    private String tokenSha256;

    @Column(name = "short_code", nullable = false, length = 8, updatable = false)
    private String shortCode;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "pdf_s3_key", length = 1024, columnDefinition = PgDomains.S3_KEY)
    private String pdfS3Key;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static Voucher issue(UUID id, UUID bookingId, UUID jti, short signingKeyId, String tokenSha256,
                                String shortCode, Instant issuedAt, Instant expiresAt) {
        Voucher voucher = new Voucher();
        voucher.id = id;
        voucher.bookingId = bookingId;
        voucher.jti = jti;
        voucher.signingKeyId = signingKeyId;
        voucher.tokenSha256 = tokenSha256;
        voucher.shortCode = shortCode;
        voucher.status = ISSUED;
        voucher.createdAt = issuedAt;
        voucher.expiresAt = expiresAt;
        return voucher;
    }
}
