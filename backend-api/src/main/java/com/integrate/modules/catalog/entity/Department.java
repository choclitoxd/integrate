package com.integrate.modules.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Departamento DIVIPOLA ({@code catalog.departments}): referencia cargada por migración, solo lectura. */
@Entity
@Immutable
@Table(name = "departments", schema = "catalog")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Department {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 2)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;
}
