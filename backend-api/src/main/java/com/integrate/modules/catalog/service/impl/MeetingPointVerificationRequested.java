package com.integrate.modules.catalog.service.impl;

import java.util.UUID;

/** Evento interno: una experiencia quedó en {@code VERIFICANDO} y hay que consultar el geocodificador (ADR-0030 §2). */
public record MeetingPointVerificationRequested(UUID experienceId) {
}
