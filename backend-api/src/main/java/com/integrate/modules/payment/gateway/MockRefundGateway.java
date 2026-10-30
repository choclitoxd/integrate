package com.integrate.modules.payment.gateway;

import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Reembolsos simulados del MVP (ADR-0029, ADR-0034 §3): sin llamadas de red. Completa siempre, con un identificador
 * derivado de la clave de idempotencia (el mismo pedido da el mismo resultado). Las pruebas fuerzan fallos con
 * {@link #next(Result)}.
 */
@Component
public class MockRefundGateway implements RefundGateway {

    private volatile Result forced;

    @Override
    public Result refund(String idempotencyKey, UUID paymentId, long amountCop) {
        Result result = forced;
        if (result != null) {
            forced = null;
            return result;
        }
        return Result.completed("mock-rf-" + Integer.toHexString(idempotencyKey.hashCode()) + "-" + amountCop);
    }

    /** Fuerza el resultado de la próxima llamada (solo pruebas). */
    public void next(Result result) {
        this.forced = result;
    }
}