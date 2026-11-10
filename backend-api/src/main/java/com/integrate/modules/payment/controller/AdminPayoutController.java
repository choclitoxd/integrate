package com.integrate.modules.payment.controller;

import com.integrate.common.payload.PageResponse;
import com.integrate.common.storage.VoucherDocumentStorage.PresignedDownload;
import com.integrate.common.web.PageRequests;
import com.integrate.modules.payment.dto.response.PayoutBatchResponse;
import com.integrate.modules.payment.dto.response.PayoutBatchResponse.Status;
import com.integrate.modules.payment.service.AdminPayoutService;
import com.integrate.modules.payment.service.AdminPayoutService.Detail;
import com.integrate.modules.payment.service.AdminPayoutService.Reconciliation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/payout-batches")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminPayoutController {

    private final AdminPayoutService service;

    @GetMapping
    public ResponseEntity<PageResponse<PayoutBatchResponse>> list(@RequestParam(required = false) Status status,
            @RequestParam(required = false) UUID associationId, @RequestParam(required = false) @Min(0) Integer page,
            @RequestParam(required = false) @Min(1) @Max(50) Integer size) {
        return noStore(service.list(status, associationId, PageRequests.newestFirst(page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Detail> detail(@PathVariable UUID id) {
        return noStore(service.detail(id));
    }

    @GetMapping("/{id}/receipt")
    public ResponseEntity<PresignedDownload> receipt(@PathVariable UUID id) {
        return noStore(service.receipt(id));
    }

    @PostMapping("/{id}/reconcile")
    public ResponseEntity<Reconciliation> reconcile(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return noStore(service.reconcile(id, UUID.fromString(jwt.getSubject())));
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
