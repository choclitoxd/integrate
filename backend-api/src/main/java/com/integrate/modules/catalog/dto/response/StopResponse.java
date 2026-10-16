package com.integrate.modules.catalog.dto.response;

import com.integrate.modules.catalog.entity.ExperienceStop;

/** Parada del recorrido, en el orden en que se visita (ADR-0022). */
public record StopResponse(String name, String description) {

    public static StopResponse from(ExperienceStop stop) {
        return new StopResponse(stop.getName(), stop.getDescription());
    }
}
