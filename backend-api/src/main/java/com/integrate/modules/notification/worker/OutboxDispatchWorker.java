package com.integrate.modules.notification.worker;

import com.integrate.modules.notification.service.impl.OutboxDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Programa el despachador del outbox (ADR-0011 §3); apagado en el perfil {@code test}. */
@Component
@ConditionalOnProperty(prefix = "integrate.notification.dispatch", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class OutboxDispatchWorker {

    private final OutboxDispatcher dispatcher;

    @Scheduled(fixedDelayString = "${integrate.notification.dispatch.interval}",
            initialDelayString = "${integrate.notification.dispatch.interval}")
    public void run() {
        dispatcher.rescueStale();
        // Vacía la cola mientras haya lotes completos; un lote parcial significa que no queda nada vencido.
        while (dispatcher.dispatchOnce() > 0 && !Thread.currentThread().isInterrupted()) {
            // otro lote
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnProperty(prefix = "integrate.notification.dispatch", name = "enabled", havingValue = "true")
    static class DispatchScheduling {
    }
}
