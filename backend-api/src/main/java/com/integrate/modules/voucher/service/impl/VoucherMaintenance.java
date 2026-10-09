package com.integrate.modules.voucher.service.impl;

import com.integrate.modules.voucher.config.VoucherProperties;
import com.integrate.modules.voucher.repository.VoucherRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Barrido durable (ADR-0010 §5): emite los vouchers de reservas confirmadas que no lo tienen y completa los PDF que
 * faltan. Cubre reinicios entre el {@code COMMIT} y la emisión inmediata, colas llenas y fallos de S3. Cada elemento
 * falla por separado sin detener el lote.
 */
@Component
@RequiredArgsConstructor
public class VoucherMaintenance {

    private static final Logger log = LoggerFactory.getLogger(VoucherMaintenance.class);

    private final VoucherRepository voucherRepository;
    private final VoucherIssuer issuer;
    private final VoucherProperties properties;

    /** @return vouchers emitidos más PDF completados en esta pasada */
    public int sweepOnce() {
        int batch = properties.sweep().batchSize();
        int done = 0;
        for (UUID bookingId : voucherRepository.findConfirmedBookingsWithoutVoucher(batch)) {
            try {
                if (issuer.issue(bookingId).isPresent()) {
                    done++;
                }
            } catch (RuntimeException e) {
                log.warn("voucher.sweep issue failed bookingId={} cause={}", bookingId, e.toString());
            }
        }
        for (UUID voucherId : voucherRepository.findIssuedWithoutPdf(batch)) {
            try {
                issuer.renderPdf(voucherId);
                done++;
            } catch (RuntimeException e) {
                log.warn("voucher.sweep pdf failed voucherId={} cause={}", voucherId, e.toString());
            }
        }
        return done;
    }
}
