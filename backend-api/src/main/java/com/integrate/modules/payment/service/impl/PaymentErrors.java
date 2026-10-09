package com.integrate.modules.payment.service.impl;

import com.integrate.common.exception.BusinessRuleException;
import com.integrate.common.exception.ErrorCode;

/** Fábrica de las excepciones del módulo {@code payment} (ADR-0009 §8). */
final class PaymentErrors {

    private PaymentErrors() {
    }

    static BusinessRuleException bookingNotFound() {
        return new BusinessRuleException(ErrorCode.RESOURCE_NOT_FOUND, "Booking not found");
    }

    static BusinessRuleException paymentNotFound() {
        return new BusinessRuleException(ErrorCode.RESOURCE_NOT_FOUND, "Payment not found");
    }

    static BusinessRuleException notPayable(String bookingStatus) {
        return new BusinessRuleException(ErrorCode.PAYMENT_BOOKING_NOT_PAYABLE,
                "The booking is " + bookingStatus + " or its hold expired; lock the slots again.")
                .with("bookingStatus", bookingStatus);
    }

    static BusinessRuleException webhookNotConfigured() {
        return new BusinessRuleException(ErrorCode.RESOURCE_NOT_FOUND, "Payment provider not configured");
    }

    static BusinessRuleException signatureInvalid() {
        return new BusinessRuleException(ErrorCode.WEBHOOK_SIGNATURE_INVALID, "The webhook signature is not valid.");
    }

    static BusinessRuleException webhookBookingNotFound() {
        return new BusinessRuleException(ErrorCode.PAYMENT_BOOKING_NOT_FOUND,
                "The event does not match any open payment.");
    }

    static BusinessRuleException amountMismatch(long expected, long received) {
        return new BusinessRuleException(ErrorCode.PAYMENT_AMOUNT_MISMATCH,
                "The event amount differs from the payment amount.")
                .with("expectedAmountCop", expected)
                .with("receivedAmountCop", received);
    }
}
