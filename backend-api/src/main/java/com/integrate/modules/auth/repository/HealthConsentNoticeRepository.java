package com.integrate.modules.auth.repository;

import com.integrate.modules.auth.entity.HealthConsentNotice;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface HealthConsentNoticeRepository extends Repository<HealthConsentNotice, String> {

    Optional<HealthConsentNotice> findById(String noticeVersion);

    Optional<HealthConsentNotice> findByPolicyIdAndApprovedForHostTrue(Short policyId);
}
