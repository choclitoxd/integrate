package com.integrate.modules.voucher.config;

import java.util.concurrent.Executor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Ejecutor acotado de la emisión inmediata (ADR-0010 §5): la respuesta del webhook a la pasarela no espera la
 * firma, el PDF ni S3. Si la cola se llena, la tarea se descarta y el barrido durable la retoma.
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
public class VoucherAsyncConfig {

    public static final String EXECUTOR = "voucherExecutor";

    @Bean(name = EXECUTOR)
    Executor voucherExecutor(VoucherProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("voucher-");
        executor.setCorePoolSize(properties.executor().core());
        executor.setMaxPoolSize(properties.executor().max());
        executor.setQueueCapacity(properties.executor().queue());
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.DiscardPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    /** {@code @Scheduled} del barrido, solo si está habilitado (apagado en el perfil {@code test}). */
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnProperty(prefix = "integrate.voucher.sweep", name = "enabled", havingValue = "true")
    static class SweepScheduling {
    }
}
