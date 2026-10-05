package com.integrate.modules.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** Catálogo RBAC ({@code auth.roles}). Lo gobiernan las migraciones: la aplicación solo lo lee (V9). */
@Entity
@Immutable
@Table(name = "roles", schema = "auth")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Role {

    @Id
    private Short id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RoleCode code;

    @Column(nullable = false, length = 80)
    private String name;
}
