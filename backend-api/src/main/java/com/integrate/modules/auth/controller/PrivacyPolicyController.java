package com.integrate.modules.auth.controller;

import com.integrate.modules.auth.dto.response.PrivacyPolicyResponse;
import com.integrate.modules.auth.service.PrivacyPolicyService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Política de tratamiento de datos vigente, pública: el registro exige su versión y el titular debe
 * poder leerla antes de aceptar (ADR-0013 §1). Cambia solo por migración, de ahí la caché de 5 min.
 */
@RestController
@RequestMapping("/api/v1/privacy-policies")
@RequiredArgsConstructor
public class PrivacyPolicyController {

    private static final CacheControl FIVE_MINUTES = CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic();

    private final PrivacyPolicyService privacyPolicyService;

    @GetMapping("/current")
    public ResponseEntity<PrivacyPolicyResponse> current() {
        return ResponseEntity.ok()
                .cacheControl(FIVE_MINUTES)
                .body(PrivacyPolicyResponse.from(privacyPolicyService.current()));
    }
}
