package com.integrate.common.exception;

import java.net.URI;
import org.springframework.http.HttpStatus;

/**
 * Catálogo único de códigos de error del contrato (docs/api/dtos_contrato_api.md §2, ADR-0003).
 * Cada constante fija el {@code status} HTTP, el {@code type} RFC 9457 y un {@code title} estable.
 * Los módulos agregan aquí sus códigos: no se admite un {@code code} en texto libre.
 */
public enum ErrorCode {

    // --- Protocolo (type: about:blank) -------------------------------------
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, null, "Bad Request"),

    // --- Transversales -------------------------------------------------------
    VALIDATION_FAILED(HttpStatus.UNPROCESSABLE_CONTENT, "validation-failed", "Validation Failed"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "resource-not-found", "Resource Not Found"),
    RBAC_FORBIDDEN(HttpStatus.FORBIDDEN, "forbidden", "Forbidden"),
    CONCURRENCY_LOCK_TIMEOUT(HttpStatus.SERVICE_UNAVAILABLE, "concurrency-lock-timeout", "Concurrency Lock Timeout"),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "idempotency-key-reused", "Idempotency Key Reused"),

    // --- Auth ----------------------------------------------------------------
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "invalid-credentials", "Invalid Credentials"),
    AUTH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "authentication-required", "Authentication Required"),
    AUTH_REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "refresh-token-invalid", "Refresh Token Invalid"),
    AUTH_ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "account-suspended", "Account Suspended"),
    AUTH_ACCOUNT_LOCKED(HttpStatus.LOCKED, "account-locked", "Account Temporarily Locked"),
    AUTH_USER_ALREADY_EXISTS(HttpStatus.CONFLICT, "user-already-exists", "User Already Exists"),
    AUTH_ACTIVATION_INVALID(HttpStatus.UNAUTHORIZED, "activation-invalid", "Activation Code Invalid"),

    // --- Enrolamiento asistido (ADR-0007) ------------------------------------
    ENROLLMENT_MANAGER_NOT_ACTIVE(HttpStatus.FORBIDDEN, "manager-not-active", "Link Manager Not Active"),
    ENROLLMENT_MANAGER_EXISTS(HttpStatus.CONFLICT, "manager-exists", "Link Manager Already Exists"),
    ENROLLMENT_DOCUMENT_INVALID(HttpStatus.UNPROCESSABLE_CONTENT, "document-invalid", "Document Invalid"),

    // --- Catalog / Booking ---------------------------------------------------
    CATALOG_EXPERIENCE_NOT_APPROVED(HttpStatus.CONFLICT, "experience-not-approved", "Experience Not Approved"),
    CATALOG_INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "invalid-status-transition", "Invalid Status Transition"),
    CATALOG_SCHEDULE_CONFLICT(HttpStatus.CONFLICT, "schedule-conflict", "Schedule Conflict"),
    CATALOG_HOST_PROFILE_EXISTS(HttpStatus.CONFLICT, "host-profile-exists", "Host Profile Already Exists"),
    CATALOG_ASSOCIATION_EXISTS(HttpStatus.CONFLICT, "association-exists", "Association Already Exists"),
    BOOKING_INSUFFICIENT_SLOTS(HttpStatus.CONFLICT, "insufficient-slots", "Insufficient Available Slots"),
    BOOKING_SCHEDULE_NOT_BOOKABLE(HttpStatus.CONFLICT, "schedule-not-bookable", "Schedule Not Bookable"),
    CONSENT_HEALTH_EMERGENCY_REQUIRED(HttpStatus.PRECONDITION_REQUIRED, "consent-required", "Consent Required"),

    // --- Payments ------------------------------------------------------------
    WEBHOOK_SIGNATURE_INVALID(HttpStatus.UNAUTHORIZED, "webhook-signature-invalid", "Webhook Signature Invalid"),
    PAYMENT_BOOKING_NOT_FOUND(HttpStatus.UNPROCESSABLE_CONTENT, "payment-booking-not-found", "Payment Booking Not Found"),
    PAYMENT_PROVIDER_MISMATCH(HttpStatus.UNPROCESSABLE_CONTENT, "payment-provider-mismatch", "Payment Provider Mismatch"),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.UNPROCESSABLE_CONTENT, "payment-amount-mismatch", "Payment Amount Mismatch"),
    PAYMENT_BOOKING_NOT_PAYABLE(HttpStatus.CONFLICT, "booking-not-payable", "Booking Not Payable"),

    // --- Voucher (ADR-0010) ----------------------------------------------------
    VOUCHER_NOT_READY(HttpStatus.CONFLICT, "voucher-not-ready", "Voucher Not Ready"),

    // --- Offline -------------------------------------------------------------
    OFFLINE_DEVICE_NOT_AUTHORIZED(HttpStatus.FORBIDDEN, "device-not-authorized", "Device Not Authorized"),
    SYNC_BATCH_PAYLOAD_MISMATCH(HttpStatus.CONFLICT, "sync-batch-payload-mismatch", "Sync Batch Payload Mismatch");

    public static final String PROBLEM_BASE_URI = "https://api.integrate.co/problems/";
    private static final URI ABOUT_BLANK = URI.create("about:blank");

    private final HttpStatus status;
    private final String slug;
    private final String title;

    ErrorCode(HttpStatus status, String slug, String title) {
        this.status = status;
        this.slug = slug;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    /** {@code about:blank} para errores de protocolo; URI de dominio en el resto. */
    public URI type() {
        return slug == null ? ABOUT_BLANK : URI.create(PROBLEM_BASE_URI + slug);
    }
}
