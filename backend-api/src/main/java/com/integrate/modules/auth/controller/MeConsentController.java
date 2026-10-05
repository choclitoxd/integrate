package com.integrate.modules.auth.controller;

import com.integrate.modules.auth.dto.request.ConsentAcceptance;
import com.integrate.modules.auth.dto.response.ConsentStatusResponse;
import com.integrate.modules.auth.service.ConsentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consentimientos del titular autenticado (RF-02, ADR-0005 §6). Cualquier rol; el titular es
 * siempre el {@code sub} del token, nunca un identificador del cuerpo o de la ruta.
 */
@RestController
@RequestMapping("/api/v1/me/consents")
@RequiredArgsConstructor
public class MeConsentController {

    private final ConsentService consentService;

    @GetMapping
    public ConsentStatusResponse status(@AuthenticationPrincipal Jwt jwt) {
        return ConsentStatusResponse.from(consentService.status(subject(jwt)));
    }

    @PostMapping
    public ConsentStatusResponse grant(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ConsentAcceptance request,
                                       HttpServletRequest http) {
        return ConsentStatusResponse.from(consentService.grant(subject(jwt), request, ClientInfos.from(http)));
    }

    private static UUID subject(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
