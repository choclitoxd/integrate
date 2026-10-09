package com.integrate.modules.booking.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Activa {@code @Scheduled} solo si el worker de expiración está habilitado (ADR-0008 §7). */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "integrate.booking.expiration", name = "enabled", havingValue = "true")
class BookingSchedulingConfig {
}
