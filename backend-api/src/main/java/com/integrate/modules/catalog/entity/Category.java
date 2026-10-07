package com.integrate.modules.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** Categoría temática ({@code catalog.categories}). Sembrada por V3; la API solo la lee. */
@Entity
@Immutable
@Table(name = "categories", schema = "catalog")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category {

    @Id
    private Short id;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
