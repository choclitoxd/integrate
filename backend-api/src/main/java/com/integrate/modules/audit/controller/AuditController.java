package com.integrate.modules.audit.controller;

import com.integrate.common.payload.PageResponse;
import com.integrate.modules.audit.dto.response.AuditRecordResponse;
import com.integrate.modules.audit.dto.response.AuditVerifyResponse;
import com.integrate.modules.audit.service.AuditQueryService;
import com.integrate.modules.audit.service.model.AuditCaller;
import com.integrate.modules.audit.service.model.AuditFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta de la bitácora de auditoría, reservada al Administrador (RF-20, ADR-0035 §1). Solo lectura: la bitácora se
 * escribe por {@code AuditTrail}. Las respuestas no se almacenan en cachés.
 */
@RestController
@RequestMapping("/api/v1/admin/audit")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AuditController {

    static final int MAX_PAGE_SIZE = 100;

    private final AuditQueryService service;

    @GetMapping
    public ResponseEntity<PageResponse<AuditRecordResponse>> search(
            @AuthenticationPrincipal Jwt jwt, HttpServletRequest request,
            @RequestParam(required = false) @Pattern(regexp = "^[a-z]{1,20}$") String module,
            @RequestParam(required = false) @Size(max = 60) String action,
            @RequestParam(required = false) @Size(max = 60) String entityName,
            @RequestParam(required = false) @Size(max = 100) String entityId,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) @Pattern(regexp = "^(INFO|WARNING|HIGH|CRITICAL)$") String severity,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) @Size(max = 100) String subject,
            @RequestParam(required = false) @Min(0) Integer page,
            @RequestParam(required = false) @Min(1) @Max(MAX_PAGE_SIZE) Integer size) {
        AuditFilter filter = new AuditFilter(module, action, entityName, entityId, actorId, severity, from, to, subject);
        return noStore(service.search(caller(jwt, request), filter, page == null ? 0 : page,
                size == null ? PageResponse.DEFAULT_SIZE : size));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @AuthenticationPrincipal Jwt jwt, HttpServletRequest request,
            @RequestParam(required = false) @Pattern(regexp = "^[a-z]{1,20}$") String module,
            @RequestParam(required = false) @Size(max = 60) String action,
            @RequestParam(required = false) @Size(max = 60) String entityName,
            @RequestParam(required = false) @Size(max = 100) String entityId,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) @Pattern(regexp = "^(INFO|WARNING|HIGH|CRITICAL)$") String severity,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) @Size(max = 100) String subject) {
        AuditFilter filter = new AuditFilter(module, action, entityName, entityId, actorId, severity, from, to, subject);
        byte[] csv = service.export(caller(jwt, request), filter);
        String stamp = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
                .format(Instant.now());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"auditoria-" + stamp + ".csv\"")
                .body(csv);
    }

    @GetMapping("/verify")
    public ResponseEntity<AuditVerifyResponse> verify(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request,
                                                      @RequestParam(required = false) @Min(1) Long fromId,
                                                      @RequestParam(required = false) @Min(1) Long toId) {
        return noStore(service.verify(caller(jwt, request), fromId, toId));
    }

    /** IP de {@code getRemoteAddr()}, nunca {@code X-Forwarded-For} leído a mano (mismo criterio que {@code auth}). */
    private static AuditCaller caller(Jwt jwt, HttpServletRequest request) {
        try {
            return new AuditCaller(UUID.fromString(jwt.getSubject()), InetAddress.getByName(request.getRemoteAddr()),
                    request.getHeader(HttpHeaders.USER_AGENT));
        } catch (UnknownHostException e) {
            throw new IllegalStateException("Servlet container returned a non-literal remote address", e);
        }
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}