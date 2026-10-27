package com.integrate.modules.catalog.service.impl;

import com.integrate.modules.catalog.meetingpoint.MeetingPointProperties;
import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Ejecutor de la verificación del punto de encuentro (ADR-0030 §2): un hilo, cola corta y descarte si se llena
 * (el anfitrión puede repetirla). Con {@code integrate.catalog.meeting-point.async=false} corre en el mismo hilo.
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
class MeetingPointAsyncConfig {

    static final String EXECUTOR = "meetingPointExecutor";

    @Bean(name = EXECUTOR)
    Executor meetingPointExecutor(MeetingPointProperties properties) {
        if (!properties.async()) {
            return new SyncTaskExecutor();
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("meeting-point-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(50);
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.DiscardPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
