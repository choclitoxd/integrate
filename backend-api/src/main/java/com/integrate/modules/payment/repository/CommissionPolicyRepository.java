package com.integrate.modules.payment.repository;

import com.integrate.modules.payment.entity.CommissionPolicy;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommissionPolicyRepository extends JpaRepository<CommissionPolicy, Short> {

    /** Política vigente en {@code now}; el {@code EXCLUDE} de V5 garantiza que haya a lo sumo una. */
    @Query("""
            select c from CommissionPolicy c
             where c.validFrom <= :now and (c.validTo is null or c.validTo > :now)""")
    Optional<CommissionPolicy> findCurrent(@Param("now") Instant now);
}
