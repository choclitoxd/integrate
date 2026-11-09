package com.integrate.modules.auth.service.impl;

import com.integrate.common.exception.BusinessRuleException;
import com.integrate.common.exception.ErrorCode;
import com.integrate.modules.auth.service.PrivacyPolicyService;
import com.integrate.modules.auth.service.model.PublishedPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class PrivacyPolicyServiceImpl implements PrivacyPolicyService {

    private final ConsentLedger ledger;

    @Override
    @Transactional(readOnly = true)
    public PublishedPolicy current() {
        return ledger.currentPolicy()
                .map(policy -> new PublishedPolicy(policy.getVersion(), policy.getTitle(), policy.getDocumentUrl(),
                        policy.getEffectiveFrom(), policy.getContentSha256()))
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.PRIVACY_POLICY_NOT_PUBLISHED));
    }
}
