package com.integrate.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Hash de contraseñas con Argon2id directo (RNF-01, ADR-0005 §3), con los parámetros mínimos de
 * OWASP: 19 MiB, 2 iteraciones, paralelismo 1. Produce {@code $argon2id$v=19$…}, compatible con
 * {@code ck_users_password_hash}; por eso no se usa {@code DelegatingPasswordEncoder} (prefijo {@code {argon2}}).
 */
@Configuration(proxyBeanMethods = false)
public class PasswordHashingConfig {

    static final int SALT_LENGTH = 16;
    static final int HASH_LENGTH = 32;
    static final int PARALLELISM = 1;
    static final int MEMORY_KIB = 19 * 1024;
    static final int ITERATIONS = 2;

    @Bean
    PasswordEncoder passwordEncoder() {
        return new Argon2PasswordEncoder(SALT_LENGTH, HASH_LENGTH, PARALLELISM, MEMORY_KIB, ITERATIONS);
    }
}
