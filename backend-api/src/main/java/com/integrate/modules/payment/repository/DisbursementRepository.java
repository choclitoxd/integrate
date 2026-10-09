package com.integrate.modules.payment.repository;

import com.integrate.modules.payment.entity.Disbursement;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DisbursementRepository extends JpaRepository<Disbursement, UUID> {
}
