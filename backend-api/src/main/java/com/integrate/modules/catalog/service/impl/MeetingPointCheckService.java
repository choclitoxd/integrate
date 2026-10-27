package com.integrate.modules.catalog.service.impl;

import com.integrate.modules.catalog.entity.Department;
import com.integrate.modules.catalog.entity.Experience;
import com.integrate.modules.catalog.entity.Municipality;
import com.integrate.modules.catalog.meetingpoint.InvalidMeetingPointTransitionException;
import com.integrate.modules.catalog.meetingpoint.MeetingPointAutomaton;
import com.integrate.modules.catalog.meetingpoint.MeetingPointEvent;
import com.integrate.modules.catalog.meetingpoint.MeetingPointState;
import com.integrate.modules.catalog.meetingpoint.MeetingPointVerifier;
import com.integrate.modules.catalog.meetingpoint.MeetingPointVerifier.Verification;
import com.integrate.modules.catalog.repository.DepartmentRepository;
import com.integrate.modules.catalog.repository.ExperienceRepository;
import com.integrate.modules.catalog.repository.MunicipalityRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Orquesta el ciclo de verificación del punto de encuentro (ADR-0030): aplica los eventos al autómata, guarda cada
 * transición en el historial y lanza la verificación. <b>No decide estados</b>: solo {@link MeetingPointAutomaton}
 * lo hace. Los métodos {@code on*}, {@code beginManualCheck}, {@code confirm} y {@code requireApprovable}
 * participan en la transacción del invocador; {@link #runVerification} abre las suyas porque consulta un servicio
 * externo y no debe mantener una transacción abierta mientras espera.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingPointCheckService {

    private final ExperienceRepository experienceRepository;
    private final MunicipalityRepository municipalityRepository;
    private final DepartmentRepository departmentRepository;
    private final MeetingPointVerifier verifier;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    /** La experiencia se envía a revisión: se verifica salvo que el administrador ya haya confirmado el punto. */
    public void onSubmit(Experience experience) {
        MeetingPointState state = experience.getMeetingPointCheckStatus();
        switch (state) {
            case CONFIRMADO -> { }
            case VERIFICANDO -> restart(experience, MeetingPointEvent.REINTENTAR);
            default -> restart(experience, MeetingPointEvent.ENVIAR);
        }
    }

    /** Cambió el punto de encuentro, sus coordenadas o su municipio: el resultado anterior ya no vale. */
    public void onEdit(Experience experience, boolean pointChanged) {
        if (!pointChanged) {
            return;
        }
        switch (experience.getMeetingPointCheckStatus()) {
            case REVIEW, MISMATCH, CONFIRMADO -> {
                apply(experience, MeetingPointEvent.EDITAR_COORDENADAS, null);
                experience.recordMeetingPointCheck(null, null, null, null);
            }
            case MATCH, UNVERIFIED -> restart(experience, MeetingPointEvent.ENVIAR);
            case VERIFICANDO -> restart(experience, MeetingPointEvent.REINTENTAR);
            default -> { }
        }
    }

    /**
     * El anfitrión pide repetir la verificación. Devuelve {@code true} si quedó en {@code VERIFICANDO} y hay que
     * ejecutarla; con el punto ya confirmado no hace nada.
     */
    public boolean beginManualCheck(Experience experience) {
        MeetingPointState state = experience.getMeetingPointCheckStatus();
        if (state == MeetingPointState.CONFIRMADO) {
            return false;
        }
        MeetingPointEvent event = state == MeetingPointState.PENDIENTE || state == MeetingPointState.CORREGIDO
                ? MeetingPointEvent.ENVIAR : MeetingPointEvent.REINTENTAR;
        apply(experience, event, null);
        experience.recordMeetingPointCheck(null, null, null, null);
        return true;
    }

    /** El administrador acepta un punto dudoso ({@code REVIEW} o {@code MISMATCH}). */
    public void confirm(Experience experience) {
        try {
            apply(experience, MeetingPointEvent.CONFIRMAR, null);
        } catch (InvalidMeetingPointTransitionException e) {
            throw CatalogErrors.invalidMeetingPointAction(e.from(), "confirm");
        }
    }

    /** Única puerta hacia la aprobación: solo {@code MATCH}, {@code REVIEW}, {@code UNVERIFIED} o {@code CONFIRMADO}. */
    public void requireApprovable(Experience experience) {
        MeetingPointState state = experience.getMeetingPointCheckStatus();
        if (state.approvable()) {
            return;
        }
        throw state == MeetingPointState.MISMATCH ? CatalogErrors.meetingPointMismatch(state)
                : CatalogErrors.meetingPointNotVerified(state);
    }

    /**
     * Ejecuta la verificación de una experiencia que está en {@code VERIFICANDO}: lee los datos, consulta el
     * geocodificador fuera de toda transacción y aplica el evento resultante. Si mientras tanto cambió el punto o
     * el estado, descarta el resultado (ya hay otra verificación en camino).
     */
    public void runVerification(UUID experienceId) {
        // REQUIRES_NEW: este método se invoca tras confirmar (AFTER_COMMIT) la transacción que lo originó, que ya no admite
        // trabajo; con un hilo aparte (async) no hay transacción previa y da igual.
        TransactionTemplate independent = requiresNew();
        Snapshot snapshot = independent.execute(status -> snapshotOf(experienceId));
        if (snapshot == null) {
            return;
        }
        Verification result;
        try {
            result = verifier.verify(snapshot.meetingPoint(), snapshot.latitude(), snapshot.longitude(),
                    snapshot.municipality(), snapshot.department());
        } catch (RuntimeException e) {
            log.warn("Meeting point verification of {} failed unexpectedly", experienceId, e);
            result = new Verification(MeetingPointEvent.SIN_RESPUESTA, null, null, null);
        }
        Verification outcome = result;
        independent.executeWithoutResult(status -> experienceRepository.findActiveByIdForUpdate(experienceId)
                .filter(e -> e.getMeetingPointCheckStatus() == MeetingPointState.VERIFICANDO)
                .filter(e -> snapshot.sameAs(e))
                .ifPresent(e -> {
                    apply(e, outcome.event(), outcome.distanceMeters());
                    e.recordMeetingPointCheck(outcome.distanceMeters(), outcome.suggestedLatitude(),
                            outcome.suggestedLongitude(), clock.instant());
                    experienceRepository.flush();
                }));
    }

    // ---------------------------------------------------------------- internos

    private TransactionTemplate requiresNew() {
        TransactionTemplate template = new TransactionTemplate(Objects.requireNonNull(
                transactions.getTransactionManager(), "transaction manager"));
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private void restart(Experience experience, MeetingPointEvent event) {
        apply(experience, event, null);
        experience.recordMeetingPointCheck(null, null, null, null);
        publisher.publishEvent(new MeetingPointVerificationRequested(experience.getId()));
    }

    private void apply(Experience experience, MeetingPointEvent event, Integer distanceMeters) {
        MeetingPointState from = experience.getMeetingPointCheckStatus();
        MeetingPointState to = MeetingPointAutomaton.next(from, event);
        experience.moveMeetingPointCheck(to);
        experienceRepository.flush();
        jdbc.update("""
                INSERT INTO catalog.meeting_point_check_history (experience_id, from_state, event, to_state, distance_m)
                VALUES (?, ?, ?, ?, ?)""", experience.getId(), from.name(), event.name(), to.name(), distanceMeters);
    }

    private Snapshot snapshotOf(UUID experienceId) {
        Experience e = experienceRepository.findById(experienceId).filter(x -> x.getDeletedAt() == null).orElse(null);
        if (e == null || e.getMeetingPointCheckStatus() != MeetingPointState.VERIFICANDO) {
            return null;
        }
        Municipality municipality = municipalityRepository.findById(e.getMunicipalityCode()).orElse(null);
        String department = municipality == null ? "" : departmentRepository.findById(municipality.getDepartmentCode())
                .map(Department::getName).orElse("");
        return new Snapshot(e.getMeetingPoint(), e.getLatitude(), e.getLongitude(), e.getMunicipalityCode(),
                municipality == null ? "" : municipality.getName(), department);
    }

    private record Snapshot(String meetingPoint, BigDecimal latitude, BigDecimal longitude, String municipalityCode,
                            String municipality, String department) {

        boolean sameAs(Experience e) {
            return Objects.equals(meetingPoint, e.getMeetingPoint())
                    && Objects.equals(municipalityCode, e.getMunicipalityCode())
                    && sameNumber(latitude, e.getLatitude()) && sameNumber(longitude, e.getLongitude());
        }

        private static boolean sameNumber(BigDecimal a, BigDecimal b) {
            return a == null || b == null ? a == b : a.compareTo(b) == 0;
        }
    }
}
