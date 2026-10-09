package com.integrate.modules.payment.repository;

import com.integrate.modules.payment.entity.Refund;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundRepository extends JpaRepository<Refund, UUID> {
}
