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

/** Municipio DIVIPOLA ({@code catalog.municipalities}, V11): solo lectura. Nombre tal como lo publica el DANE. */
@Entity
@Immutable
@Table(name = "municipalities", schema = "catalog")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Municipality {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 5)
    private String code;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "department_code", nullable = false, length = 2)
    private String departmentCode;

    @Column(nullable = false, length = 100)
    private String name;
}
