package com.integrate.modules.payment.service.impl;

import com.integrate.common.audit.AuditEntry;
import com.integrate.common.audit.AuditEntry.Severity;
import com.integrate.common.audit.AuditTrail;
import com.integrate.modules.payment.entity.PaymentProvider;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deja constancia de un webhook rechazado por firma o marca de tiempo (ADR-0009 §4). Va en su propia transacción
 * porque la petición termina en 401 y el evento no se guarda en la bandeja de entrada. Una racha de estos avisos
 * suele indicar una rotación de secretos mal hecha.
 */
@Component
@RequiredArgsConstructor
class WebhookRejectionAudit {

    private final AuditTrail auditTrail;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(PaymentProvider provider, String reason) {
        auditTrail.record(new AuditEntry("payments", "payment_webhook_events", provider.name(),
                "PAYMENT_WEBHOOK_REJECTED", Severity.WARNING, null, null, null, Map.of("reason", reason)));
    }
}
