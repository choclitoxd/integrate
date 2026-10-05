package com.integrate.common.exception;

import com.integrate.common.payload.InvalidParam;
import com.integrate.common.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import java.net.URI;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Único punto de traducción excepción → {@code application/problem+json} (RFC 9457, ADR-0003).
 * Todo cuerpo de error incluye {@code traceId} (= cabecera {@code X-Trace-Id}) e {@code instance}.
 * Los errores 5xx nunca exponen {@code detail} ni mensajes internos.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    static final String PROP_CODE = "code";
    static final String PROP_TRACE_ID = "traceId";
    static final String PROP_INVALID_PARAMS = "invalidParams";
    private static final String POSTGRES_LOCK_NOT_AVAILABLE = "55P03";
    private static final String LOCK_RETRY_AFTER_SECONDS = "1";
    private static final URI ABOUT_BLANK = URI.create("about:blank");

    // ------------------------------------------------------------------ dominio

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<Object> handleBusinessRule(BusinessRuleException ex, WebRequest request) {
        ProblemDetail body = problem(ex.getErrorCode(), ex.getDetail());
        ex.getExtensions().forEach(body::setProperty);
        HttpHeaders headers = new HttpHeaders();
        headers.addAll(ex.getHeaders());
        return handleExceptionInternal(ex, body, headers, ex.getErrorCode().status(), request);
    }

    // --------------------------------------------------------------- validación (422)

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<InvalidParam> params = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(fe -> params.add(fieldParam(fe)));
        ex.getBindingResult().getGlobalErrors()
                .forEach(ge -> params.add(new InvalidParam(ge.getObjectName(), reason(ge))));
        return validationFailed(ex, params, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<InvalidParam> params = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            if (result instanceof ParameterErrors errors) {
                errors.getFieldErrors().forEach(fe -> params.add(fieldParam(fe)));
            } else {
                String name = result.getMethodParameter().getParameterName();
                result.getResolvableErrors().forEach(err -> params.add(new InvalidParam(name, reason(err))));
            }
        }
        return validationFailed(ex, params, headers, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
        List<InvalidParam> params = ex.getConstraintViolations().stream()
                .map(v -> new InvalidParam(paramName(v.getPropertyPath()), v.getMessage()))
                .toList();
        return validationFailed(ex, params, new HttpHeaders(), request);
    }

    // ----------------------------------------------------------- protocolo (400)

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // No se refleja el mensaje del parser: puede contener fragmentos del cuerpo enviado.
        ProblemDetail body = problem(ErrorCode.MALFORMED_REQUEST,
                "The request body is malformed or contains incompatible types.");
        return handleExceptionInternal(ex, body, headers, ErrorCode.MALFORMED_REQUEST.status(), request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = problem(ErrorCode.MALFORMED_REQUEST, "A parameter has an incompatible type.");
        if (ex.getPropertyName() != null) {
            body.setProperty(PROP_INVALID_PARAMS, List.of(new InvalidParam(ex.getPropertyName(), "Incompatible type")));
        }
        return handleExceptionInternal(ex, body, headers, ErrorCode.MALFORMED_REQUEST.status(), request);
    }

    // --------------------------------------------------------- recursos (404)

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = problem(ErrorCode.RESOURCE_NOT_FOUND, null);
        return handleExceptionInternal(ex, body, headers, ErrorCode.RESOURCE_NOT_FOUND.status(), request);
    }

    // ------------------------------------------------- seguridad a nivel de método

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        return handleExceptionInternal(ex, problem(ErrorCode.RBAC_FORBIDDEN, null), new HttpHeaders(),
                ErrorCode.RBAC_FORBIDDEN.status(), request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Object> handleAuthentication(AuthenticationException ex, WebRequest request) {
        return handleExceptionInternal(ex, problem(ErrorCode.AUTH_TOKEN_INVALID, null), new HttpHeaders(),
                ErrorCode.AUTH_TOKEN_INVALID.status(), request);
    }

    // -------------------------------------------- concurrencia (503) y genérico (500)

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<Object> handleLockTimeout(Exception ex, WebRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, LOCK_RETRY_AFTER_SECONDS);
        return handleExceptionInternal(ex, problem(ErrorCode.CONCURRENCY_LOCK_TIMEOUT, null), headers,
                ErrorCode.CONCURRENCY_LOCK_TIMEOUT.status(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        if (hasSqlState(ex, POSTGRES_LOCK_NOT_AVAILABLE)) {
            return handleLockTimeout(ex, request);
        }
        log.error("Unhandled exception", ex);
        // Sin detail ni code: nunca se exponen detalles internos (server.error.include-*: never).
        ProblemDetail body = ProblemDetail.forStatus(500);
        return handleExceptionInternal(ex, body, new HttpHeaders(), HttpStatusCode.valueOf(500), request);
    }

    // ------------------------------------------------------- enriquecimiento común

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body == null && ex instanceof ErrorResponse errorResponse) {
            body = errorResponse.updateAndGetBody(getMessageSource(), LocaleContextHolder.getLocale());
        }
        if (body == null) {
            body = ProblemDetail.forStatus(statusCode);
        }
        if (body instanceof ProblemDetail problem) {
            enrich(problem, request);
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    private void enrich(ProblemDetail problem, WebRequest request) {
        // `type` es obligatorio en el contrato; Spring omite about:blank si no se fijó explícitamente.
        URI type = problem.getType();
        problem.setType(type != null ? type : ABOUT_BLANK);
        if (request instanceof ServletWebRequest servletRequest) {
            HttpServletRequest http = servletRequest.getRequest();
            // Solo la ruta, sin query string: evita reflejar datos personales en el cuerpo.
            problem.setInstance(URI.create(http.getRequestURI()));
            String traceId = TraceIdFilter.currentTraceId(http);
            if (traceId != null) {
                problem.setProperty(PROP_TRACE_ID, traceId);
            }
        }
    }

    private ResponseEntity<Object> validationFailed(
            Exception ex, List<InvalidParam> params, HttpHeaders headers, WebRequest request) {
        ProblemDetail body = problem(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid.");
        body.setProperty(PROP_INVALID_PARAMS, params);
        return handleExceptionInternal(ex, body, headers, ErrorCode.VALIDATION_FAILED.status(), request);
    }

    private static ProblemDetail problem(ErrorCode code, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(code.status(), detail);
        body.setType(code.type());
        body.setTitle(code.title());
        body.setProperty(PROP_CODE, code.name());
        return body;
    }

    private static InvalidParam fieldParam(FieldError fe) {
        return new InvalidParam(fe.getField(), reason(fe));
    }

    private static String reason(MessageSourceResolvable error) {
        return error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value";
    }

    /** Ruta de la violación sin el nodo del método (p. ej. {@code lock.request.size} → {@code request.size}). */
    private static String paramName(Path path) {
        Iterator<Path.Node> nodes = path.iterator();
        String full = path.toString();
        if (nodes.hasNext()) {
            Path.Node first = nodes.next();
            if ((first.getKind() == ElementKind.METHOD || first.getKind() == ElementKind.CONSTRUCTOR)
                    && full.length() > first.getName().length()) {
                return full.substring(first.getName().length() + 1);
            }
        }
        return full;
    }

    private static boolean hasSqlState(Throwable ex, String sqlState) {
        for (Throwable t = ex; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof SQLException sql && sqlState.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
