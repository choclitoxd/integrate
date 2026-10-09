package com.integrate.modules.booking.worker;

import com.integrate.modules.booking.config.BookingProperties;
import com.integrate.modules.booking.service.BookingExpirationService;
import com.integrate.modules.booking.service.model.ExpiredBatch;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Worker de expiración (RF-11, ADR-0008 §7). Cada {@code integrate.booking.expiration.interval} procesa lotes
 * hasta que uno llega incompleto. Varias instancias pueden correrlo a la vez sin coordinación gracias a
 * {@code SKIP LOCKED}.
 */
@Component
@ConditionalOnProperty(prefix = "integrate.booking.expiration", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class BookingExpirationWorker {

    private static final Logger log = LoggerFactory.getLogger(BookingExpirationWorker.class);

    private final BookingExpirationService expirationService;
    private final BookingProperties properties;

    @Scheduled(fixedDelayString = "${integrate.booking.expiration.interval}",
            initialDelayString = "${integrate.booking.expiration.interval}")
    public void run() {
        long started = System.nanoTime();
        int batchSize = properties.expiration().batchSize();
        int expired = 0;
        int releasedSlots = 0;
        ExpiredBatch batch;
        do {
            batch = expirationService.expireBatch(batchSize);
            expired += batch.expired();
            releasedSlots += batch.releasedSlots();
        } while (batch.expired() == batchSize);
        if (expired > 0) {
            log.info("booking.expiration expired={} releasedSlots={} durationMs={}", expired, releasedSlots,
                    (System.nanoTime() - started) / 1_000_000);
        }
    }
}
