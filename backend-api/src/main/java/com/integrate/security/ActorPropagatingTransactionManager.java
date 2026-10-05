package com.integrate.security;

import jakarta.persistence.EntityManagerFactory;
import java.util.UUID;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Propaga el usuario autenticado a PostgreSQL al iniciar cada transacción (ADR-0005 §10):
 * {@code set_config('integrate.actor_id', <sub>, true)}. Lo leen {@code common.current_actor()}, los
 * {@code DEFAULT} de {@code created_by} y los historiales de estado. El alcance es la transacción
 * ({@code is_local = true}), así que el valor nunca se filtra a otra petición por el pool de conexiones.
 * Sin usuario autenticado (autorregistro, procesos del sistema) no se fija nada y el actor queda {@code NULL}.
 */
public class ActorPropagatingTransactionManager extends JpaTransactionManager {

    static final String SET_ACTOR_SQL = "SELECT set_config('integrate.actor_id', :actor, true)";

    public ActorPropagatingTransactionManager(EntityManagerFactory emf) {
        super(emf);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);
        UUID actor = currentActor();
        if (actor == null) {
            return;
        }
        EntityManagerHolder holder =
                (EntityManagerHolder) TransactionSynchronizationManager.getResource(obtainEntityManagerFactory());
        if (holder != null) {
            holder.getEntityManager().createNativeQuery(SET_ACTOR_SQL)
                    .setParameter("actor", actor.toString())
                    .getSingleResult();
        }
    }

    static UUID currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token) || !token.isAuthenticated()) {
            return null;
        }
        try {
            return UUID.fromString(token.getName());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
