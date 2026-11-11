package com.integrate.modules.auth.repository;

import com.integrate.modules.auth.entity.HabeasDataConsent;
import com.integrate.modules.auth.entity.ConsentPurpose;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HabeasDataConsentRepository extends JpaRepository<HabeasDataConsent, Long> {

    /**
     * Historial completo del titular, del más reciente al más antiguo (mismo orden que
     * {@code auth.v_current_consents}). Son pocas filas por usuario: el último acto por
     * finalidad se resuelve en memoria.
     */
    List<HabeasDataConsent> findByUserIdOrderByOccurredAtDescIdDesc(UUID userId);

    Optional<HabeasDataConsent> findFirstByUserIdAndPurposeOrderByOccurredAtDescIdDesc(
            UUID userId, ConsentPurpose purpose);

    /** Toma el candado transaccional por titular que también toma el trigger de V19 al insertar actos de salud. */
    @Query(value = "SELECT 1 FROM (SELECT auth.lock_health_consent(CAST(:userId AS uuid))) AS locked",
            nativeQuery = true)
    int lockHealthConsent(@Param("userId") UUID userId);

    /** Un formato físico firmado respalda el consentimiento de un solo titular (ADR-0007 §5). */
    boolean existsByPhysicalConsentFormS3Key(String physicalConsentFormS3Key);
}
