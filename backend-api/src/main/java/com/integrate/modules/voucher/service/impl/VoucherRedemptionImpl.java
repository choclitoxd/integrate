package com.integrate.modules.voucher.service.impl;

import com.integrate.modules.booking.service.BookingExecution;
import com.integrate.modules.voucher.repository.VoucherRepository;
import com.integrate.modules.voucher.service.VoucherRedemption;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class VoucherRedemptionImpl implements VoucherRedemption {

    private final VoucherRepository vouchers;
    private final BookingExecution bookings;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void redeem(UUID voucherId, UUID bookingId, Instant at) {
        if (vouchers.consume(voucherId, at) != 1) {
            throw new IllegalStateException("Voucher " + voucherId + " is not ISSUED");
        }
        bookings.markExecuted(bookingId);
    }
}
