package com.integrate.modules.auth.service.model;

/** Texto inmutable publicado para informar el uso de EPS. */
public record HealthNotice(String noticeVersion, String body, String contentSha256) {
}
