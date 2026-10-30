package com.integrate.modules.payment.gateway;

import java.util.UUID;

/**
 * Puerto de pago a la asociación (ADR-0034 §5). La clave de idempotencia ({@code payout:<lote>}) hace seguro repetir
 * la llamada tras una caída entre el envío y el cierre del lote: la pasarela no paga dos veces la misma clave.
 */
public interface PayoutGateway {

    Result payout(String idempotencyKey, UUID batchId, UUID payoutAccountId, long amountCop);

    /** @param gatewayPayoutId identificador del pago en la pasarela cuando se completó */
    record Result(Outcome outcome, String gatewayPayoutId, String failureReason) {

        public static Result completed(String gatewayPayoutId) {
            return new Result(Outcome.COMPLETED, gatewayPayoutId, null);
        }

        public static Result rejected(String reason) {
            return new Result(Outcome.REJECTED, null, reason);
        }

        /** Error transitorio: el lote queda {@code SUBMITTED} y se reintenta en el siguiente barrido. */
        public static Result retry() {
            return new Result(Outcome.RETRY, null, null);
        }
    }

    enum Outcome {
        COMPLETED, REJECTED, RETRY
    }
}