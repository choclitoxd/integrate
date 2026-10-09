package com.integrate.modules.payment.repository;

import com.integrate.modules.payment.entity.PaymentProvider;
import com.integrate.modules.payment.entity.PaymentStatus;
import com.integrate.modules.payment.entity.PaymentTransaction;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    /** Intento más reciente de la reserva en alguno de los estados indicados. */
    Optional<PaymentTransaction> findFirstByBookingIdAndProviderAndStatusInOrderByCreatedAtDesc(
            UUID bookingId, PaymentProvider provider, Collection<PaymentStatus> statuses);

    Optional<PaymentTransaction> findByProviderAndGatewayTransactionId(PaymentProvider provider,
                                                                       String gatewayTransactionId);

    boolean existsByBookingIdAndStatus(UUID bookingId, PaymentStatus status);
}
