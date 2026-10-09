package com.integrate.modules.payment.entity;

/**
 * Valores de {@code ck_payment_transactions_provider} (V5). El contrato nombra al Mock {@code MOCK_GATEWAY}; en la
 * base se persiste {@code MOCK} (contrato §6).
 */
public enum PaymentProvider {
    MOCK("MOCK_GATEWAY"),
    WOMPI("WOMPI"),
    EPAYCO("EPAYCO");

    private final String contractName;

    PaymentProvider(String contractName) {
        this.contractName = contractName;
    }

    /** Nombre en el contrato ({@code CanonicalPaymentEvent.provider}, {@code WebhookAck.provider}). */
    public String contractName() {
        return contractName;
    }
}
