package com.integrate.common.audit;

/**
 * Bitácora encadenada {@code audit.audit_trail} (RF-20, ADR-0008 §6). Escribe dentro de la transacción en
 * curso, así que la fila se confirma o se revierte junto con la operación auditada.
 *
 * <p>El trigger de encadenamiento bloquea la fila única {@code audit.chain_head} hasta el {@code COMMIT}: toda
 * transacción que audita se serializa ahí. Por eso {@link #record(AuditEntry)} debe ser la <b>última</b>
 * sentencia de la transacción.
 */
public interface AuditTrail {

    /** @throws org.springframework.transaction.IllegalTransactionStateException si no hay transacción activa */
    void record(AuditEntry entry);
}
