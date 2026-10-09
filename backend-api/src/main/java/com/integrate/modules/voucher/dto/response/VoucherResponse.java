package com.integrate.modules.voucher.dto.response;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Voucher visto por el turista dueño de la reserva (ADR-0010 §1).
 *
 * @param token     JWT compacto re-derivado y verificado contra {@code token_sha256}, para mostrar el QR; {@code null}
 *                  si ya no se puede re-derivar (llave rotada): en ese caso se usa el PDF
 * @param validDate fecha local de la franja en la que el voucher es válido en campo
 * @param pdfStatus {@code READY} o {@code GENERATING}
 * @param pdfUrl    URL prefirmada de corta vigencia, o {@code null} mientras se genera el PDF
 */
public record VoucherResponse(UUID voucherId, UUID jti, UUID bookingId, String shortCode, String status,
                              Instant issuedAt, Instant expiresAt, LocalDate validDate, String token, String pdfStatus,
                              URI pdfUrl, Instant pdfUrlExpiresAt) {
}
