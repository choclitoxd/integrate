package com.integrate.modules.payment.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Activa {@code @Scheduled} solo si los trabajadores de pagos están habilitados (ADR-0034 §7); apagado en pruebas. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "integrate.payments.workers", name = "enabled", havingValue = "true")
class PaymentSchedulingConfig {
}