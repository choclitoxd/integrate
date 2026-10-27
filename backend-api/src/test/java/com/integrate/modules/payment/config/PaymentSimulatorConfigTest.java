package com.integrate.modules.payment.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.integrate.modules.payment.controller.DevPaymentSimulatorController;
import com.integrate.modules.payment.entity.PaymentProvider;
import com.integrate.modules.payment.service.PaymentWebhookService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** ADR-0029: el simulador depende de una propiedad explícita y nunca convive con una pasarela real. */
class PaymentSimulatorConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(PaymentWebhookService.class, () -> mock(PaymentWebhookService.class))
            .withUserConfiguration(DevPaymentSimulatorController.class);

    @Test
    void simulatorIsOffByDefault() {
        assertThat(new PaymentProperties(PaymentProvider.MOCK, null, null, null).simulator().enabled()).isFalse();
        runner.run(context -> assertThat(context).doesNotHaveBean(DevPaymentSimulatorController.class));
    }

    @Test
    void simulatorEndpointExistsOnlyWhenEnabled() {
        runner.withPropertyValues("integrate.payments.simulator.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(DevPaymentSimulatorController.class));
        runner.withPropertyValues("integrate.payments.simulator.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(DevPaymentSimulatorController.class));
    }

    @Test
    void simulatorWithMockProviderIsAllowed() {
        PaymentProperties properties = new PaymentProperties(PaymentProvider.MOCK, null, null,
                new PaymentProperties.Simulator(true));
        assertThat(properties.simulator().enabled()).isTrue();
    }

    @Test
    void simulatorNextToRealGatewayFailsFast() {
        assertThatThrownBy(() -> new PaymentProperties(PaymentProvider.WOMPI, null, null,
                new PaymentProperties.Simulator(true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requires integrate.payments.provider=MOCK");
    }
}
