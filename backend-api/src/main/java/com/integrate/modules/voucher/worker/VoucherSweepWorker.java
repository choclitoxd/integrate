package com.integrate.modules.voucher.worker;

import com.integrate.modules.voucher.service.impl.VoucherMaintenance;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Programa el barrido durable de vouchers (ADR-0010 §5); apagado en el perfil {@code test}. */
@Component
@ConditionalOnProperty(prefix = "integrate.voucher.sweep", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class VoucherSweepWorker {

    private static final Logger log = LoggerFactory.getLogger(VoucherSweepWorker.class);

    private final VoucherMaintenance maintenance;

    @Scheduled(fixedDelayString = "${integrate.voucher.sweep.interval}",
            initialDelayString = "${integrate.voucher.sweep.interval}")
    public void run() {
        long started = System.nanoTime();
        int done = maintenance.sweepOnce();
        if (done > 0) {
            log.info("voucher.sweep completed={} durationMs={}", done, (System.nanoTime() - started) / 1_000_000);
        }
    }
}
