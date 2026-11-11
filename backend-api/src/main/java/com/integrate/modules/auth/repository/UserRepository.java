package com.integrate.modules.auth.repository;

import com.integrate.modules.auth.entity.RoleCode;
import com.integrate.modules.auth.entity.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** Cuentas abiertas (ni anonimizadas ni dadas de baja), para el listado del administrador (ADR-0048 §1). */
    @Query("""
            select u from User u
             where u.deletedAt is null and u.status <> com.integrate.modules.auth.entity.UserStatus.ANONYMIZED""")
    Page<User> findOpenAccounts(Pageable pageable);

    /** Igual que {@link #findOpenAccounts} pero solo las cuentas con el rol vigente indicado. */
    @Query("""
            select u from User u
             where u.deletedAt is null and u.status <> com.integrate.modules.auth.entity.UserStatus.ANONYMIZED
               and exists (select 1 from UserRole ur join Role r on r.id = ur.roleId
                            where ur.userId = u.id and ur.revokedAt is null and r.code = :role)""")
    Page<User> findOpenAccountsByRole(@Param("role") RoleCode role, Pageable pageable);

    /** Usa el índice {@code uq_users_email_lower} ({@code lower(email)}). */
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findByDocumentIndex(byte[] documentIndex);

    /** {@code SELECT … FOR UPDATE}: serializa los intentos de login concurrentes sobre la misma cuenta. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);
}
