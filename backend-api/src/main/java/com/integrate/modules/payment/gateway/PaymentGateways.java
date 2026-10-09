package com.integrate.modules.payment.gateway;

import com.integrate.modules.payment.config.PaymentProperties;
import com.integrate.modules.payment.entity.PaymentProvider;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Adaptadores configurados por proveedor (ADR-0009 §2). El proveedor activo ({@code integrate.payments.provider})
 * debe tener sus secretos o el arranque aborta; un adaptador sin secretos no atiende su webhook (404).
 */
@Component
public class PaymentGateways {

    private final Map<PaymentProvider, PaymentGatewayAdapter> configured = new EnumMap<>(PaymentProvider.class);
    private final PaymentGatewayAdapter active;

    public PaymentGateways(List<PaymentGatewayAdapter> adapters, PaymentProperties properties) {
        adapters.stream().filter(PaymentGatewayAdapter::configured).forEach(a -> configured.put(a.provider(), a));
        this.active = configured.get(properties.provider());
        if (active == null) {
            throw new IllegalStateException("integrate.payments.provider=" + properties.provider()
                    + " has no configured adapter: set its secrets (ADR-0009 §7)");
        }
    }

    /** Adaptador de los cobros nuevos. */
    public PaymentGatewayAdapter active() {
        return active;
    }

    /** Adaptador que atiende el webhook de {@code provider}, si tiene sus secretos. */
    public Optional<PaymentGatewayAdapter> forWebhook(PaymentProvider provider) {
        return Optional.ofNullable(configured.get(provider));
    }
}
