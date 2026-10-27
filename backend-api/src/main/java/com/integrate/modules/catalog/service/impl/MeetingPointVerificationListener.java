package com.integrate.modules.catalog.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Lanza la verificación <b>después de confirmar</b> la transacción que dejó la experiencia en {@code VERIFICANDO}
 * y fuera del hilo de la solicitud (ADR-0030 §2): responder al anfitrión no espera al geocodificador.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class MeetingPointVerificationListener {

    private final MeetingPointCheckService service;

    @Async(MeetingPointAsyncConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void on(MeetingPointVerificationRequested event) {
        try {
            service.runVerification(event.experienceId());
        } catch (RuntimeException e) {
            // La experiencia queda en VERIFICANDO: el anfitrión puede repetirla (REINTENTAR) sin bloquear nada más.
            log.warn("Meeting point verification of {} could not run", event.experienceId(), e);
        }
    }
}
