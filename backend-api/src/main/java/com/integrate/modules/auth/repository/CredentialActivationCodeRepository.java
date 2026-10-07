package com.integrate.modules.auth.repository;

import com.integrate.modules.auth.entity.CredentialActivationCode;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CredentialActivationCodeRepository extends JpaRepository<CredentialActivationCode, Long> {

    /**
     * Código vivo del usuario (índice parcial {@code uq_activation_codes_user_live}) con
     * {@code SELECT … FOR UPDATE}: dos canjes simultáneos se serializan y el contador de intentos no pierde golpes.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c from CredentialActivationCode c
             where c.userId = :userId and c.consumedAt is null and c.revokedAt is null""")
    Optional<CredentialActivationCode> findLiveForUpdate(@Param("userId") UUID userId);
}
