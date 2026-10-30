package com.integrate.modules.offline.service;

import com.integrate.modules.offline.dto.response.RevocationsResponse;
import com.integrate.modules.offline.dto.response.RosterResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Descarga preventiva del padrón y de la lista delta de revocación (RF-16, ADR-0032 §2 y §3). */
public interface RosterService {

    /**
     * @param date       jornada; {@code null} = hoy en {@code America/Bogota}
     * @param scheduleId limita el padrón a una franja, para el caso en que supere el tope
     */
    RosterResponse download(UUID userId, String deviceIdentifier, String userAgent, LocalDate date, UUID scheduleId);

    /** @param since {@code null} = todas las revocaciones aún relevantes */
    RevocationsResponse revocations(UUID userId, String deviceIdentifier, String userAgent, Instant since);
}
