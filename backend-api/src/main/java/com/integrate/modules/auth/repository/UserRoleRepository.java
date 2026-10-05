package com.integrate.modules.auth.repository;

import com.integrate.modules.auth.entity.RoleCode;
import com.integrate.modules.auth.entity.UserRole;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
