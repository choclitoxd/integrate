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

/** Tabla puente N:M ({@code catalog.experience_categories}); la única del catálogo con DELETE físico (V9). */
@Entity
@IdClass(ExperienceCategory.Key.class)
@Table(name = "experience_categories", schema = "catalog")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExperienceCategory {

    @Id
    @Column(name = "experience_id")
    private UUID experienceId;

    @Id
    @Column(name = "category_id")
    private Short categoryId;

    public ExperienceCategory(UUID experienceId, Short categoryId) {
        this.experienceId = experienceId;
        this.categoryId = categoryId;
    }

    public record Key(UUID experienceId, Short categoryId) implements Serializable {

        @SuppressWarnings("unused") // requerido por JPA
        public Key() {
            this(null, null);
        }
    }
}
