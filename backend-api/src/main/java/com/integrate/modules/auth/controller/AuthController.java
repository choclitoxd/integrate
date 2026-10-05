package com.integrate.modules.auth.controller;

import com.integrate.common.exception.BusinessRuleException;
import com.integrate.common.exception.ErrorCode;
import com.integrate.modules.auth.dto.request.LoginRequest;
import com.integrate.modules.auth.dto.request.RegisterRequest;
import com.integrate.modules.auth.dto.response.AuthTokenResponse;
import com.integrate.modules.auth.dto.response.RegisterResponse;
import com.integrate.modules.auth.service.AuthService;
import com.integrate.modules.auth.service.RegistrationService;
import com.integrate.modules.auth.service.model.RegisteredUser;
import com.integrate.modules.auth.service.model.Session;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Sesión y autorregistro (RF-01, RF-02; contrato {@code Auth}, ADR-0005). Todos los endpoints son públicos. */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RegistrationService registrationService;

    @PostMapping("/login")
    public ResponseEntity<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request,
                                                   HttpServletRequest http) {
        return sessionResponse(authService.login(request, ClientInfos.from(http)));
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request,
                                                     HttpServletRequest http) {
        RegisteredUser user = registrationService.registerTourist(request, ClientInfos.from(http));
        return ResponseEntity.created(URI.create("/api/v1/users/" + user.userId()))
                .body(RegisterResponse.from(user));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthTokenResponse> refresh(
            @CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken, HttpServletRequest http) {
        try {
            return sessionResponse(authService.refresh(refreshToken, ClientInfos.from(http)));
        } catch (BusinessRuleException e) {
            if (e.getErrorCode() == ErrorCode.AUTH_REFRESH_TOKEN_INVALID) {
                // Un token inútil no debe seguir viajando: se borra junto con el 401.
                throw e.withHeader(HttpHeaders.SET_COOKIE, RefreshCookies.clear());
            }
            throw e;
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, RefreshCookies.clear()).build();
    }

    private static ResponseEntity<AuthTokenResponse> sessionResponse(Session session) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, RefreshCookies.issue(session.refreshToken(), session.refreshTokenTtl()))
                .body(AuthTokenResponse.from(session));
    }
}
