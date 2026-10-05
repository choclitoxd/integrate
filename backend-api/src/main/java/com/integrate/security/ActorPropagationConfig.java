package com.integrate.security;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Reemplaza el {@code JpaTransactionManager} autoconfigurado (que se retira por
 * {@code @ConditionalOnMissingBean}) por uno que propaga el actor a PostgreSQL (ADR-0005 §10).
 */
@Configuration(proxyBeanMethods = false)
public class ActorPropagationConfig {

    @Bean
    PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new ActorPropagatingTransactionManager(entityManagerFactory);
    }
}
