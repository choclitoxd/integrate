package com.integrate.modules.payment.gateway;

/**
 * La firma del webhook no es válida o su marca de tiempo está fuera de la ventana. El mensaje describe la causa para
 * la auditoría y nunca contiene secretos ni firmas esperadas.
 */
public class WebhookRejectedException extends RuntimeException {

    public WebhookRejectedException(String reason) {
        super(reason);
    }
}
