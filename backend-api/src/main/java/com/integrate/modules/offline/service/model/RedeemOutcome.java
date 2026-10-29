package com.integrate.modules.offline.service.model;

import com.integrate.common.exception.ErrorCode;
import com.integrate.modules.offline.dto.response.RedeemResponse;
import java.time.Instant;

/**
 * Resultado del canje de contingencia. Un rechazo es un resultado y no una excepción: la evidencia que lo registra
 * debe confirmarse aunque la respuesta sea 4xx (ADR-0032 §4); el controlador lo traduce a error HTTP.
 *
 * @param accepted     presente si el canje se aceptó
 * @param rejection    código del rechazo, o {@code null} si se aceptó
 * @param redeemedAt   en {@code VOUCHER_ALREADY_REDEEMED}, cuándo se canjeó antes
 */
public record RedeemOutcome(RedeemResponse accepted, ErrorCode rejection, Instant redeemedAt) {

    public static RedeemOutcome accepted(RedeemResponse response) {
        return new RedeemOutcome(response, null, null);
    }

    public static RedeemOutcome rejected(ErrorCode code) {
        return new RedeemOutcome(null, code, null);
    }

    public static RedeemOutcome alreadyRedeemed(Instant redeemedAt) {
        return new RedeemOutcome(null, ErrorCode.VOUCHER_ALREADY_REDEEMED, redeemedAt);
    }
}
