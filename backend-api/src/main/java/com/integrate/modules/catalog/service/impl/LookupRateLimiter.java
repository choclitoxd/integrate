package com.integrate.modules.catalog.service.impl;

import com.integrate.common.exception.BusinessRuleException;
import com.integrate.common.exception.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * Límite de búsquedas de personas por administrador: {@value #MAX_LOOKUPS} por ventana de un minuto (ADR-0043 §1).
 * Frena el barrido de correos o documentos con una cuenta comprometida. Es un contador en memoria: el despliegue es
 * de nodo único (ADR-0027) y un reinicio solo reabre la ventana. Cuentan los intentos, con o sin coincidencia.
 */
@Component
@RequiredArgsConstructor
class LookupRateLimiter {

    static final int MAX_LOOKUPS = 20;
    static final Duration WINDOW = Duration.ofMinutes(1);

    private final Clock clock;
    private final Map<UUID, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    /** @throws BusinessRuleException {@code ADMIN_LOOKUP_RATE_LIMITED} con {@code Retry-After} en segundos */
    void acquire(UUID adminId) {
        Instant now = clock.instant();
        Deque<Instant> window = attempts.computeIfAbsent(adminId, id -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && !window.peekFirst().isAfter(now.minus(WINDOW))) {
                window.pollFirst();
            }
            if (window.size() >= MAX_LOOKUPS) {
                long retryAfter = Math.max(1, Duration.between(now, window.peekFirst().plus(WINDOW)).toSeconds());
                throw new BusinessRuleException(ErrorCode.ADMIN_LOOKUP_RATE_LIMITED)
                        .withHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
            }
            window.addLast(now);
        }
    }
}
