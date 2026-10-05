package com.integrate.modules.auth.repository;

import com.integrate.modules.auth.entity.PrivacyPolicy;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrivacyPolicyRepository extends JpaRepository<PrivacyPolicy, Short> {

    /** Única versión vigente (índice parcial {@code uq_privacy_policies_current}). */
    Optional<PrivacyPolicy> findByCurrentTrue();
}
