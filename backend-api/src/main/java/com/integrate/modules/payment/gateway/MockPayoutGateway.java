package com.integrate.modules.payment.gateway;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Pagos simulados del MVP (ADR-0029, ADR-0034 §5): sin llamadas de red. Completa siempre, con un identificador
 * derivado de la clave de idempotencia. Las pruebas fuerzan fallos con {@link #next(Result)}.
 */
@Component
public class MockPayoutGateway implements PayoutGateway {

    private volatile Result forced;
    private final ConcurrentHashMap<String, Result> results = new ConcurrentHashMap<>();

    @Override
    public Result payout(String idempotencyKey, UUID batchId, UUID payoutAccountId, long amountCop) {
        return results.compute(idempotencyKey, (key, previous) ->
                previous != null && previous.outcome() != Outcome.RETRY ? previous : send(key, amountCop));
    }

    @Override
    public Optional<Result> lookup(String idempotencyKey, String gatewayPayoutId) {
        return Optional.ofNullable(results.get(idempotencyKey));
    }

    private synchronized Result send(String idempotencyKey, long amountCop) {
        Result result = forced;
        if (result != null) {
            forced = null;
            return result;
        }
        return Result.completed("mock-po-" + Integer.toHexString(idempotencyKey.hashCode()) + "-" + amountCop);
    }

    /** Fuerza el resultado de la próxima llamada (solo pruebas). */
    public void next(Result result) {
        this.forced = result;
    }
}
