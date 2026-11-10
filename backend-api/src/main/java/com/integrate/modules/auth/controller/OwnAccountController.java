package com.integrate.modules.auth.controller;
import com.integrate.modules.auth.dto.request.ChangeOwnPasswordRequest;
import com.integrate.modules.auth.dto.request.UpdateOwnProfileRequest;
import com.integrate.modules.auth.dto.response.OwnProfileResponse;
import com.integrate.modules.auth.service.impl.OwnAccountService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController
@RequestMapping("/api/v1/auth/me")
@RequiredArgsConstructor
public class OwnAccountController {
    private final OwnAccountService service;
    @GetMapping
    public ResponseEntity<OwnProfileResponse> get(@AuthenticationPrincipal Jwt jwt, HttpServletRequest http) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.get(UUID.fromString(jwt.getSubject()), ClientInfos.from(http)));
    }
    @PatchMapping
    public ResponseEntity<OwnProfileResponse> update(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateOwnProfileRequest request, HttpServletRequest http) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.update(UUID.fromString(jwt.getSubject()), request, ClientInfos.from(http)));
    }
    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangeOwnPasswordRequest request, HttpServletRequest http) {
        service.changePassword(UUID.fromString(jwt.getSubject()), request, ClientInfos.from(http));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, RefreshCookies.clear()).build();
    }
}
