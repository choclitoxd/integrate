package com.integrate.modules.auth.repository;

import com.integrate.modules.auth.entity.RoleCode;
import com.integrate.modules.auth.entity.UserRole;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    /** Roles vigentes ({@code revoked_at IS NULL}) del usuario: fuente del claim {@code roles}. */
    @Query("""
            select r.code from UserRole ur join Role r on r.id = ur.roleId
             where ur.userId = :userId and ur.revokedAt is null
             order by r.id""")
    List<RoleCode> findActiveRoleCodes(@Param("userId") UUID userId);

    @Query("""
            select count(ur) > 0 from UserRole ur join Role r on r.id = ur.roleId
             where r.code = :code and ur.revokedAt is null""")
    boolean existsActiveAssignment(@Param("code") RoleCode code);

    /**
     * Cierra la asignación vigente del rol (ADR-0038 §2). La hora la pone la base ({@code now()}, la misma que
     * llena {@code created_at}): {@code ck_user_roles_revocation} exige {@code revoked_at >= created_at} y el reloj
     * de la aplicación puede ir unos milisegundos por detrás del de PostgreSQL.
     *
     * @return filas cerradas: 0 si el usuario no tenía el rol vigente
     */
    @Modifying
    @Query(value = """
            UPDATE auth.user_roles ur SET revoked_at = now(), revoked_by = :revokedBy
              FROM auth.roles r
             WHERE r.id = ur.role_id AND r.code = :code AND ur.user_id = :userId AND ur.revoked_at IS NULL""",
            nativeQuery = true)
    int revokeActive(@Param("userId") UUID userId, @Param("code") String code, @Param("revokedBy") UUID revokedBy);
}
