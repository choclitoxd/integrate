package com.integrate.modules.catalog.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

/**
 * Motivo de negocio de la transacción en curso para los historiales de estado:
 * {@code set_config('integrate.status_reason', :reason, true)}, leído por {@code common.current_reason()}
 * en {@code fn_experience_status_log} (ADR-0006 §4). Alcance de transacción: no se filtra por el pool.
 */
@Repository
public class StatusReasonContext {

    @PersistenceContext
    private EntityManager entityManager;

    /** Debe invocarse dentro de la transacción y antes del UPDATE de estado. */
    public void set(String reason) {
        entityManager.createNativeQuery("SELECT set_config('integrate.status_reason', :reason, true)")
                .setParameter("reason", reason == null ? "" : reason)
                .getSingleResult();
    }
}
