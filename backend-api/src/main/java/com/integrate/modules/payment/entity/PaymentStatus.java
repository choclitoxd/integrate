package com.integrate.modules.payment.entity;

/** Valores de {@code ck_payment_transactions_status} (V5). Un estado final no cambia ({@code fn_payment_update_guard}). */
public enum PaymentStatus {
    INITIATED, PENDING, APPROVED, REJECTED, ERROR, VOIDED;

    public boolean isFinal() {
        return this == APPROVED || this == REJECTED || this == VOIDED;
    }
}
