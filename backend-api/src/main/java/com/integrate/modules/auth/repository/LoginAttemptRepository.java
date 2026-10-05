package com.integrate.modules.auth.repository;

import com.integrate.modules.auth.entity.LoginAttempt;
import com.integrate.modules.auth.entity.LoginFailureReason;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {

    @Query("select max(a.attemptedAt) from LoginAttempt a where a.userId = :userId and a.succeeded = true")
    Optional<Instant> findLastSuccessAt(@Param("userId") UUID userId);

    /** Fallos del motivo indicado posteriores a {@code since} (índice {@code ix_login_attempts_user_failed}). */
    @Query("""
            select count(a) from LoginAttempt a
             where a.userId = :userId and a.succeeded = false
               and a.failureReason = :reason and a.attemptedAt > :since""")
    long countFailuresSince(@Param("userId") UUID userId, @Param("reason") LoginFailureReason reason,
                            @Param("since") Instant since);
}
