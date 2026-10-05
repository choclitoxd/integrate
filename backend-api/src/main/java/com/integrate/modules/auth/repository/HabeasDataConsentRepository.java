package com.integrate.modules.auth.repository;

import com.integrate.modules.auth.entity.HabeasDataConsent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HabeasDataConsentRepository extends JpaRepository<HabeasDataConsent, Long> {

    /**
     * Historial completo del titular, del más reciente al más antiguo (mismo orden que
     * {@code auth.v_current_consents}). Son pocas filas por usuario: el último acto por
     * finalidad se resuelve en memoria.
     */
    List<HabeasDataConsent> findByUserIdOrderByOccurredAtDescIdDesc(UUID userId);
}
