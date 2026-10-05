package com.integrate.modules.auth.repository;

import com.integrate.modules.auth.entity.RefreshToken;
import com.integrate.modules.auth.entity.RevokeReason;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /** {@code SELECT … FOR UPDATE}: dos rotaciones simultáneas del mismo token se serializan. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t where t.tokenSha256 = :hash")
    Optional<RefreshToken> findByTokenSha256ForUpdate(@Param("hash") String tokenSha256);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update RefreshToken t set t.revokedAt = :now, t.revokeReason = :reason
             where t.userId = :userId and t.revokedAt is null""")
    int revokeAllActive(@Param("userId") UUID userId, @Param("reason") RevokeReason reason,
                        @Param("now") Instant now);
}
