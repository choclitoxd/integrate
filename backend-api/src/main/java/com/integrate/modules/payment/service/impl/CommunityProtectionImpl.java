package com.integrate.modules.payment.service.impl;

import com.integrate.modules.payment.repository.DisbursementRepository;
import com.integrate.modules.payment.service.CommunityProtection;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class CommunityProtectionImpl implements CommunityProtection {

    private final DisbursementRepository disbursements;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void release(UUID bookingId, Instant at) {
        disbursements.releaseCancelledOfBooking(bookingId, at);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal refundedAmount(UUID bookingId) {
        return disbursements.refundedAmountOfBooking(bookingId);
    }
}