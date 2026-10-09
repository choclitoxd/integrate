package com.integrate.modules.payment.gateway;

/**
 * Evento con firma válida que no es de transacciones (p. ej. tokens de Nequi en Wompi). Se responde 200 sin efectos
 * para que la pasarela no lo reintente.
 */
public class IgnoredEventException extends RuntimeException {

    private final String eventType;

    public IgnoredEventException(String eventType) {
        super("Ignored webhook event type " + eventType);
        this.eventType = eventType;
    }

    public String eventType() {
        return eventType;
    }
}
