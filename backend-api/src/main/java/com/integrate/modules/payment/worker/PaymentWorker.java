package com.integrate.modules.payment.worker;

import com.integrate.modules.payment.service.impl.RefundProcessor;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Disparador único de los trabajos de pagos (ADR-0034 §7): reembolsos, liberación de dispersiones y pago a las
 * asociaciones, en ese orden. La lógica vive en servicios idempotentes y seguros con varias instancias; este worker
 * solo los planifica y se apaga por propiedad (RNF-26).
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "integrate.payments.workers", name = "enabled", havingValue = "true")
public class PaymentWorker {

    private static final Logger log = LoggerFactory.getLogger(PaymentWorker.class);

    private final RefundProcessor refundProcessor;

    @Scheduled(fixedDelayString = "${integrate.payments.workers.interval}",
            initialDelayString = "${integrate.payments.workers.interval}")
    public void run() {
        try {
            refundProcessor.sweepOnce();
        } catch (RuntimeException e) {
            log.warn("payments.refund sweep failed cause={}", e.toString());
        }
    }
}