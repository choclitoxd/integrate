package com.integrate.modules.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Parada ordenada del recorrido ({@code catalog.experience_stops}); se reemplaza completa al editar (ADR-0022). */
@Entity
@IdClass(ExperienceStop.Key.class)
@Table(name = "experience_stops", schema = "catalog")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExperienceStop {

    @Id
    @Column(name = "experience_id")
    private UUID experienceId;

    @Id
    @Column(name = "position")
    private Short position;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    public ExperienceStop(UUID experienceId, short position, String name, String description) {
        this.experienceId = experienceId;
        this.position = position;
        this.name = name;
        this.description = description;
    }

    public record Key(UUID experienceId, Short position) implements Serializable {

        @SuppressWarnings("unused") // requerido por JPA
        public Key() {
            this(null, null);
        }
    }
}
