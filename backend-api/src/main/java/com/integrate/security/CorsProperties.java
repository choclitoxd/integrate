package com.integrate.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Orígenes permitidos para la PWA ({@code CORS_ALLOWED_ORIGINS}, separados por coma). Vacío por
 * defecto: en local el proxy de Vite hace todo same-origin y no se necesita CORS (ADR-0004, ADR-0005 §9).
 */
@ConfigurationProperties("integrate.cors")
public record CorsProperties(@DefaultValue List<String> allowedOrigins) {
}
