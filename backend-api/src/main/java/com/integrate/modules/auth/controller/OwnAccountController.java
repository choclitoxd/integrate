package com.integrate.modules.auth.controller;

import com.integrate.modules.auth.dto.request.ChangeOwnPasswordRequest;
import com.integrate.modules.auth.dto.request.ReauthRequest;
import com.integrate.modules.auth.dto.request.UpdateOwnProfileRequest;
import com.integrate.modules.auth.dto.response.OwnProfileResponse;
import com.integrate.modules.auth.dto.response.ReauthResponse;
import com.integrate.modules.auth.service.impl.OwnAccountService;
import com.integrate.modules.auth.service.impl.OwnReauthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/me")
@RequiredArgsConstructor
public class OwnAccountController {

    /** Token de confirmación de identidad (ADR-0058). Lo exigen las ediciones de la cuenta propia. */
    static final String REAUTH_HEADER = "X-Reauth-Token";

    private final OwnAccountService service;
    private final OwnReauthService reauth;

    @GetMapping
    public ResponseEntity<OwnProfileResponse> get(@AuthenticationPrincipal Jwt jwt, HttpServletRequest http) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.get(UUID.fromString(jwt.getSubject()), ClientInfos.from(http)));
    }

    @PostMapping("/reauth")
    public ResponseEntity<ReauthResponse> reauth(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ReauthRequest request, HttpServletRequest http) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(reauth.issue(UUID.fromString(jwt.getSubject()), request.password(), ClientInfos.from(http)));
    }

    @PatchMapping
    public ResponseEntity<OwnProfileResponse> update(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = REAUTH_HEADER, required = false) String reauthToken,
            @Valid @RequestBody UpdateOwnProfileRequest request, HttpServletRequest http) {
        UUID id = UUID.fromString(jwt.getSubject());
        reauth.require(id, reauthToken);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.update(id, request, ClientInfos.from(http)));
    }

    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = REAUTH_HEADER, required = false) String reauthToken,
            @Valid @RequestBody ChangeOwnPasswordRequest request, HttpServletRequest http) {
        UUID id = UUID.fromString(jwt.getSubject());
        reauth.require(id, reauthToken);
        service.changePassword(id, request, ClientInfos.from(http));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, RefreshCookies.clear()).build();
    }
}
