package com.integrate.security;

import com.integrate.common.web.TraceIdFilter;
import com.integrate.security.jwt.JwtAccessTokenIssuer;
import java.time.Duration;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Cadena de seguridad de la API (ADR-0005 §1, §5, §9): stateless, deny-by-default, access token
 * JWT RS256 validado por el resource server de Spring Security y 401/403 en RFC 9457.
 *
 * <p>CSRF desactivado: la API no usa sesión de servidor y los únicos endpoints autenticados por
 * cookie ({@code /auth/refresh}, {@code /auth/logout}) quedan protegidos por {@code SameSite=Strict},
 * {@code Path=/api/v1/auth} y la política CORS; ninguno modifica datos de negocio.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, ProblemAuthenticationEntryPoint entryPoint,
                                               ProblemAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, PublicAuthEndpoints.PATHS).permitAll()
                        // Catálogo público (RF-04, ADR-0006 §6): lectura anónima.
                        .requestMatchers(HttpMethod.GET, "/api/v1/experiences", "/api/v1/experiences/*",
                                "/api/v1/experiences/*/availability", "/api/v1/catalog/**").permitAll()
                        // Paneles por rol; además @PreAuthorize en cada controlador (defensa en profundidad).
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/host/**").hasRole("HOST")
                        // Enrolamiento asistido (ADR-0007 §1): gestor acreditado o administrador.
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register-assisted", "/api/v1/gestor/uploads")
                        .hasAnyRole("LINK_MANAGER", "ADMIN")
                        .requestMatchers("/api/v1/gestor/**").hasRole("LINK_MANAGER")
                        // Reservas y su pago (ADR-0008 §1, ADR-0009 §1): solo el turista.
                        .requestMatchers("/api/v1/bookings/**").hasRole("TOURIST")
                        // Webhooks de pasarela (ADR-0009 §4): sin JWT; la autenticidad la da la firma.
                        .requestMatchers(HttpMethod.POST, "/api/v1/payments/webhook/mock",
                                "/api/v1/payments/webhook/wompi").permitAll()
                        // Simulador de pagos (ADR-0009 §1, punto C): el controlador solo existe en dev/test.
                        .requestMatchers("/api/v1/dev/**").hasRole("TOURIST")
                        // Destino interno de los errores del contenedor; nunca expone detalles (application.yml).
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(rs -> rs
                        .bearerTokenResolver(new PublicAuthEndpoints())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    /** Claim {@code roles} (ya con prefijo {@code ROLE_}) → authorities; {@code sub} (UUID) → nombre del principal. */
    static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(JwtAccessTokenIssuer.ROLES_CLAIM);
        authorities.setAuthorityPrefix("");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    /** Sin orígenes configurados no se registra ninguna regla: el navegador bloquea toda petición cross-origin. */
    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (!properties.allowedOrigins().isEmpty()) {
            CorsConfiguration cors = new CorsConfiguration();
            cors.setAllowedOrigins(properties.allowedOrigins());
            cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
            cors.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, "Idempotency-Key",
                    TraceIdFilter.HEADER));
            cors.setExposedHeaders(List.of(TraceIdFilter.HEADER, HttpHeaders.RETRY_AFTER, HttpHeaders.LOCATION));
            // La cookie refresh_token solo viaja con credenciales.
            cors.setAllowCredentials(true);
            cors.setMaxAge(Duration.ofHours(1));
            source.registerCorsConfiguration("/api/**", cors);
        }
        return source;
    }
}
