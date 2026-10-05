package com.integrate.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fuente única de tiempo (ADR-0003). El código de dominio inyecta {@link Clock} en lugar de
 * llamar a {@code Instant.now()}/{@code LocalDateTime.now()}, para que las reglas temporales
 * (TTL de 15 min, regla de 72 h, desfase de reloj offline) sean deterministas en pruebas.
 */
@Configuration(proxyBeanMethods = false)
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
