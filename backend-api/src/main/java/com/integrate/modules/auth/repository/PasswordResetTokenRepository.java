package com.integrate.modules.auth.repository;

import com.integrate.modules.auth.entity.PasswordResetToken;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    /** {@code SELECT … FOR UPDATE}: dos canjes simultáneos del mismo enlace se serializan y solo uno gana. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PasswordResetToken t where t.tokenSha256 = :hash")
    Optional<PasswordResetToken> findByTokenSha256ForUpdate(@Param("hash") String tokenSha256);

    /** Token vivo del usuario (índice parcial {@code uq_password_reset_tokens_user_live}). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select t from PasswordResetToken t
             where t.userId = :userId and t.consumedAt is null and t.revokedAt is null""")
    Optional<PasswordResetToken> findLiveForUpdate(@Param("userId") UUID userId);

    /** Solicitudes de la cuenta en la ventana del límite (índice {@code ix_password_reset_tokens_user_requested}). */
    long countByUserIdAndRequestedAtAfter(UUID userId, Instant since);
}
