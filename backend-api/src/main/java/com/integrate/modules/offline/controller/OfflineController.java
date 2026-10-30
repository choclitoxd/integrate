package com.integrate.modules.offline.controller;

import com.integrate.common.exception.BusinessRuleException;
import com.integrate.modules.offline.dto.request.OfflineSyncBatchRequest;
import com.integrate.modules.offline.dto.request.RedeemRequest;
import com.integrate.modules.offline.dto.response.OfflineSyncBatchResponse;
import com.integrate.modules.offline.dto.response.RedeemResponse;
import com.integrate.modules.offline.dto.response.RevocationsResponse;
import com.integrate.modules.offline.dto.response.RosterResponse;
import com.integrate.modules.offline.service.ContingencyService;
import com.integrate.modules.offline.service.RosterService;
import com.integrate.modules.offline.service.SyncBatchService;
import com.integrate.modules.offline.service.model.RedeemOutcome;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operaciones offline del Gestor de Enlace (ADR-0032). Las respuestas llevan datos personales o de canje: nunca se
 * almacenan en cachés. La acreditación {@code ACTIVE} se exige en el servicio.
 */
@RestController
@RequestMapping("/api/v1/offline")
@PreAuthorize("hasRole('LINK_MANAGER')")
@RequiredArgsConstructor
public class OfflineController {

    private static final String DEVICE_HEADER = "X-Device-Id";

    private final RosterService rosterService;
    private final ContingencyService contingencyService;
    private final SyncBatchService syncBatchService;

    @GetMapping("/roster")
    public ResponseEntity<RosterResponse> roster(@AuthenticationPrincipal Jwt jwt,
                                                 @RequestHeader(DEVICE_HEADER) String deviceId,
                                                 @RequestHeader(value = HttpHeaders.USER_AGENT, required = false)
                                                 String userAgent,
                                                 @RequestParam(required = false)
                                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                 @RequestParam(required = false) UUID scheduleId) {
        return noStore(rosterService.download(UUID.fromString(jwt.getSubject()), deviceId, userAgent, date,
                scheduleId));
    }

    @GetMapping("/revocations")
    public ResponseEntity<RevocationsResponse> revocations(@AuthenticationPrincipal Jwt jwt,
                                                           @RequestHeader(DEVICE_HEADER) String deviceId,
                                                           @RequestHeader(value = HttpHeaders.USER_AGENT,
                                                                   required = false) String userAgent,
                                                           @RequestParam(required = false)
                                                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                           Instant since) {
        return noStore(rosterService.revocations(UUID.fromString(jwt.getSubject()), deviceId, userAgent, since));
    }

    @PostMapping("/contingency/redeem")
    public ResponseEntity<RedeemResponse> redeem(@AuthenticationPrincipal Jwt jwt,
                                                 @RequestHeader(value = DEVICE_HEADER, required = false)
                                                 String deviceId,
                                                 @RequestHeader(value = HttpHeaders.USER_AGENT, required = false)
                                                 String userAgent,
                                                 @Valid @RequestBody RedeemRequest request) {
        RedeemOutcome outcome = contingencyService.redeem(UUID.fromString(jwt.getSubject()), request.shortCode(),
                deviceId, userAgent);
        if (outcome.rejection() != null) {
            BusinessRuleException error = new BusinessRuleException(outcome.rejection());
            if (outcome.redeemedAt() != null) {
                error.with("redeemedAt", outcome.redeemedAt());
            }
            throw error;
        }
        return noStore(outcome.accepted());
    }

    /** Gestor y anfitrión sincronizan lo capturado sin conexión (ADR-0033); el contrato fija 200 siempre que el lote sea válido. */
    @PostMapping("/sync")
    @PreAuthorize("hasAnyRole('LINK_MANAGER', 'HOST')")
    public ResponseEntity<OfflineSyncBatchResponse> sync(@AuthenticationPrincipal Jwt jwt,
                                                         @RequestHeader(value = HttpHeaders.USER_AGENT,
                                                                 required = false) String userAgent,
                                                         @Valid @RequestBody OfflineSyncBatchRequest request) {
        return noStore(syncBatchService.sync(UUID.fromString(jwt.getSubject()), userAgent, request));
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
