package com.integrate.modules.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Asignación N:M usuario-rol ({@code auth.user_roles}). Revocar = fijar {@code revoked_at}; nunca se borra. */
@Entity
@Table(name = "user_roles", schema = "auth")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "role_id", nullable = false, updatable = false)
    private Short roleId;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by")
    private UUID revokedBy;

    public static UserRole grant(UUID userId, Role role) {
        UserRole userRole = new UserRole();
        userRole.userId = userId;
        userRole.roleId = role.getId();
        return userRole;
    }}
