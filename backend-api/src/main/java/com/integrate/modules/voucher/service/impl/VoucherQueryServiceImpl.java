package com.integrate.modules.voucher.service.impl;

import com.integrate.common.exception.BusinessRuleException;
import com.integrate.common.exception.ErrorCode;
import com.integrate.common.storage.VoucherDocumentStorage;
import com.integrate.common.storage.VoucherDocumentStorage.PresignedDownload;
import com.integrate.modules.booking.service.BookingVouchers;
import com.integrate.modules.booking.service.model.VoucherSubject;
import com.integrate.modules.catalog.service.ScheduleDirectory;
import com.integrate.modules.catalog.service.model.ScheduleSummary;
import com.integrate.modules.voucher.config.VoucherProperties;
import com.integrate.modules.voucher.dto.response.VoucherResponse;
import com.integrate.modules.voucher.entity.Voucher;
import com.integrate.modules.voucher.repository.VoucherRepository;
import com.integrate.modules.voucher.service.VoucherQueryService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VoucherQueryServiceImpl implements VoucherQueryService {

    static final String RETRY_AFTER_SECONDS = "2";

    private final BookingVouchers bookingVouchers;
    private final ScheduleDirectory scheduleDirectory;
    private final VoucherRepository voucherRepository;
    private final VoucherIssuer issuer;
    private final VoucherDocumentStorage storage;
    private final VoucherProperties properties;

    @Override
    public VoucherResponse get(UUID touristId, UUID bookingId) {
        VoucherSubject subject = bookingVouchers.subjectOf(bookingId)
                .filter(s -> s.touristId().equals(touristId))
                .orElseThrow(VoucherQueryServiceImpl::notFound);
        Voucher voucher = voucherRepository.findByBookingId(bookingId).orElseThrow(() -> subject.isConfirmed()
                ? new BusinessRuleException(ErrorCode.VOUCHER_NOT_READY, "The voucher is being issued; retry shortly.")
                        .withHeader("Retry-After", RETRY_AFTER_SECONDS)
                : notFound());
        ScheduleSummary schedule = scheduleDirectory.describe(subject.scheduleId())
                .orElseThrow(() -> new IllegalStateException("Booking " + bookingId + " has no schedule"));

        String token = issuer.rederive(voucher, subject, schedule).orElse(null);
        PresignedDownload download = voucher.getPdfS3Key() == null ? null
                : storage.presignDownload(voucher.getPdfS3Key(), properties.downloadUrlTtl());
        return new VoucherResponse(voucher.getId(), voucher.getJti(), bookingId, voucher.getShortCode(),
                voucher.getStatus(), voucher.getCreatedAt(), voucher.getExpiresAt(), VoucherClaims.validDate(schedule),
                token, download == null ? "GENERATING" : "READY", download == null ? null : download.url(),
                download == null ? null : download.expiresAt());
    }

    /** 404 también para reservas ajenas o sin voucher: no revela qué identificadores existen. */
    private static BusinessRuleException notFound() {
        return new BusinessRuleException(ErrorCode.RESOURCE_NOT_FOUND, "Voucher not found");
    }
}
