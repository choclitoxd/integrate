package com.integrate.modules.auth.repository;

import com.integrate.modules.auth.entity.PrivacyPolicy;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PrivacyPolicyRepository extends JpaRepository<PrivacyPolicy, Short> {

    /** Única versión vigente (índice parcial {@code uq_privacy_policies_current}). */
    Optional<PrivacyPolicy> findByCurrentTrue();

    /** Retiene la política hasta terminar la captura del acto, la reserva o la consulta de EPS. */
    @Query(value = "SELECT * FROM auth.privacy_policies WHERE is_current FOR SHARE", nativeQuery = true)
    Optional<PrivacyPolicy> lockCurrentForHealthConsent();
}
