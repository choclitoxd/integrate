package com.integrate.common.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpHeaders;

/**
 * Excepción de dominio única (ADR-0003). Transporta el {@link ErrorCode}, un {@code detail}
 * opcional, extensiones RFC 9457 (p. ej. {@code availableSlots}, {@code lockedUntil}) y
 * cabeceras de respuesta (p. ej. {@code Retry-After}). {@link GlobalExceptionHandler} la
 * traduce a {@code application/problem+json}; ninguna capa construye errores HTTP a mano.
 *
 * <pre>{@code
 * throw new BusinessRuleException(ErrorCode.BOOKING_INSUFFICIENT_SLOTS)
 *         .with("availableSlots", 2);
 * }</pre>
 */
public class BusinessRuleException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String detail;
    private final Map<String, Object> extensions = new LinkedHashMap<>();
    private final HttpHeaders headers = new HttpHeaders();

    public BusinessRuleException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public BusinessRuleException(ErrorCode errorCode, String detail) {
        super(errorCode.name() + (detail == null ? "" : ": " + detail));
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
        this.detail = detail;
    }

    /** Agrega una extensión RFC 9457 (miembro adicional del cuerpo del problema). */
    public BusinessRuleException with(String name, Object value) {
        extensions.put(name, value);
        return this;
    }

    /** Agrega una cabecera a la respuesta de error (p. ej. {@code Retry-After}). */
    public BusinessRuleException withHeader(String name, String value) {
        headers.add(name, value);
        return this;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public String getDetail() {
        return detail;
    }

    public Map<String, Object> getExtensions() {
        return Collections.unmodifiableMap(extensions);
    }

    public HttpHeaders getHeaders() {
        return HttpHeaders.readOnlyHttpHeaders(headers);
    }
}
