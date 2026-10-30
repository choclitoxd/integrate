package com.integrate.modules.payment.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Reglas de dinero (ADR-0034 §1): pesos colombianos enteros y redondeo explícito. Funciones puras, sin I/O. La suma
 * comunidad + plataforma es siempre el bruto; el peso que sobra del redondeo va a la comunidad en el reparto y a la
 * plataforma en el reembolso.
 */
public final class MoneyRules {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private MoneyRules() {
    }

    /** Porción comunitaria del bruto: {@code CEIL(bruto × pct / 100)} (F3, RF-12). */
    public static BigDecimal communityShare(BigDecimal grossCop, BigDecimal communityPct) {
        return grossCop.multiply(communityPct).divide(HUNDRED, 0, RoundingMode.CEILING);
    }

    /** Lo que queda para la plataforma: el bruto menos la porción comunitaria. */
    public static BigDecimal platformShare(BigDecimal grossCop, BigDecimal communityShare) {
        return grossCop.subtract(communityShare);
    }

    /** Reembolso por cancelación anticipada: {@code FLOOR(bruto × pct / 100)}; el resto lo retiene la plataforma. */
    public static BigDecimal earlyRefund(BigDecimal grossCop, BigDecimal refundPct) {
        return grossCop.multiply(refundPct).divide(HUNDRED, 0, RoundingMode.FLOOR);
    }

    /**
     * Desglose de un comprobante de lote (RF-19): la comisión de plataforma se deriva (bruto − neto) para cumplir el
     * {@code CHECK} de {@code settlement_receipts}; la comisión de la pasarela sale de ella.
     */
    public static SettlementBreakdown settlement(BigDecimal grossCop, BigDecimal gatewayFeeCop,
                                                 BigDecimal netCommunityCop) {
        return new SettlementBreakdown(grossCop, gatewayFeeCop, grossCop.subtract(netCommunityCop), netCommunityCop);
    }

    public record SettlementBreakdown(BigDecimal grossCop, BigDecimal gatewayFeeCop, BigDecimal platformFeeCop,
                                      BigDecimal netCommunityCop) {
    }
}