package com.integrate.modules.payment.gateway;

/**
 * Puerto de reembolsos hacia la pasarela (ADR-0034 §3). La clave de idempotencia hace seguro reintentar tras una caída:
 * pedir dos veces el mismo reembolso devuelve el mismo resultado.
 */
public interface RefundGateway {

    Result refund(String idempotencyKey, java.util.UUID paymentId, long amountCop);

    /** @param gatewayRefundId identificador en la pasarela cuando se completó */
    record Result(Outcome outcome, String gatewayRefundId, String failureReason) {

        public static Result completed(String gatewayRefundId) {
            return new Result(Outcome.COMPLETED, gatewayRefundId, null);
        }

        public static Result rejected(String reason) {
            return new Result(Outcome.REJECTED, null, reason);
        }

        /** Error transitorio (red, indisponibilidad): la orden se reintenta en el siguiente barrido. */
        public static Result retry() {
            return new Result(Outcome.RETRY, null, null);
        }
    }

    enum Outcome {
        COMPLETED, REJECTED, RETRY
    }
}