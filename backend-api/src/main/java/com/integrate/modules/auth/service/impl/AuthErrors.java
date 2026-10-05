package com.integrate.modules.auth.service.impl;

import com.integrate.common.exception.BusinessRuleException;
import com.integrate.common.exception.ErrorCode;
import com.integrate.common.payload.InvalidParam;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.http.HttpHeaders;

/** Fábrica de las excepciones del módulo {@code auth}: un solo lugar fija códigos y extensiones del contrato. */
final class AuthErrors {

    private AuthErrors() {
    }

    static BusinessRuleException invalidCredentials() {
        return new BusinessRuleException(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }

    static BusinessRuleException accountSuspended() {
        return new BusinessRuleException(ErrorCode.AUTH_ACCOUNT_SUSPENDED);
    }

    /** 423 con {@code lockedUntil} (extensión del contrato) y {@code Retry-After} en segundos. */
    static BusinessRuleException accountLocked(Instant lockedUntil, Instant now) {
        long retryAfter = Math.max(1, Duration.between(now, lockedUntil).toSeconds());
        return new BusinessRuleException(ErrorCode.AUTH_ACCOUNT_LOCKED)
                .with("lockedUntil", lockedUntil.truncatedTo(ChronoUnit.SECONDS).toString())
                .withHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
    }

    static BusinessRuleException refreshTokenInvalid() {
        return new BusinessRuleException(ErrorCode.AUTH_REFRESH_TOKEN_INVALID);
    }

    static BusinessRuleException userAlreadyExists() {
        return new BusinessRuleException(ErrorCode.AUTH_USER_ALREADY_EXISTS);
    }

    /** 422 con un único {@code invalidParams}, igual que una violación de Bean Validation. */
    static BusinessRuleException invalidField(String field, String reason) {
        return new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid.")
                .with("invalidParams", List.of(new InvalidParam(field, reason)));
    }
}
