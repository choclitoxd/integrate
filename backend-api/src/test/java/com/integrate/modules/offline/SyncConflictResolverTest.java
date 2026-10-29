package com.integrate.modules.offline;

import static org.assertj.core.api.Assertions.assertThat;

import com.integrate.modules.offline.service.model.SyncDecision;
import com.integrate.modules.offline.service.model.SyncDecision.Effect;
import com.integrate.modules.offline.service.model.SyncFacts;
import com.integrate.modules.offline.service.impl.SyncConflictResolver;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Tabla de decisión de ADR-0033 §3, celda por celda y sin base de datos. */
class SyncConflictResolverTest {

    private static final Instant RECEIVED = Instant.parse("2026-11-07T20:00:00Z");
    private static final LocalDate DAY = LocalDate.parse("2026-11-07");
    /** 10:00 en Bogotá del día válido. */
    private static final Instant SCANNED = Instant.parse("2026-11-07T15:00:00Z");

    private static SyncFacts facts(Instant scannedAt, boolean known, String voucher, String booking,
                                   LocalDate validDate, boolean alreadyAccepted) {
        return new SyncFacts(RECEIVED, scannedAt, known, voucher, booking, validDate, alreadyAccepted);
    }

    private static SyncFacts ok() {
        return facts(SCANNED, true, "ISSUED", "CONFIRMED", DAY, false);
    }

    @Test
    void aCleanScanIsAcceptedAndRedeemed() {
        SyncDecision decision = SyncConflictResolver.decide(ok());

        assertThat(decision.serverOutcome()).isEqualTo("ACCEPTED");
        assertThat(decision.status()).isEqualTo("CONFIRMED");
        assertThat(decision.incidentType()).isNull();
        assertThat(decision.effects()).containsExactlyInAnyOrder(Effect.PERSIST_EVIDENCE, Effect.REDEEM);
    }

    @Test
    void aNoShowBookingCanStillBeRedeemed() {
        SyncDecision decision = SyncConflictResolver.decide(facts(SCANNED, true, "ISSUED", "NO_SHOW", DAY, false));

        assertThat(decision.serverOutcome()).isEqualTo("ACCEPTED");
        assertThat(decision.effects()).contains(Effect.REDEEM);
    }

    @Test
    void aScanMoreThanFiveMinutesInTheFutureIsAClockSkewConflict() {
        SyncDecision decision = SyncConflictResolver.decide(
                facts(RECEIVED.plus(Duration.ofMinutes(5)).plusSeconds(1), true, "ISSUED", "CONFIRMED", DAY, false));

        assertThat(decision.serverOutcome()).isEqualTo("REJECTED_CLOCK_SKEW");
        assertThat(decision.status()).isEqualTo("CONFLICT");
        assertThat(decision.incidentType()).isEqualTo("CLOCK_SKEW_FUTURE_SCAN");
        assertThat(decision.incidentSeverity()).isEqualTo("MEDIUM");
        assertThat(decision.effects()).containsExactly(Effect.PERSIST_EVIDENCE);
    }

    @Test
    void exactlyFiveMinutesAheadIsStillWithinTolerance() {
        // El borde es estricto (> 5 min): 5 min exactos pasan al resto de reglas; la fecha local manda.
        SyncDecision decision = SyncConflictResolver.decide(
                facts(RECEIVED.plus(Duration.ofMinutes(5)), true, "ISSUED", "CONFIRMED",
                        LocalDate.parse("2026-11-07"), false));

        assertThat(decision.serverOutcome()).isEqualTo("ACCEPTED");
    }

    @Test
    void aMissingVoucherIsRejectedWithoutEvidenceNorIncident() {
        SyncDecision decision = SyncConflictResolver.decide(facts(SCANNED, false, null, null, null, false));

        assertThat(decision.serverOutcome()).isEqualTo("REJECTED_UNKNOWN_VOUCHER");
        assertThat(decision.status()).isEqualTo("REJECTED");
        assertThat(decision.incidentType()).isNull();
        assertThat(decision.effects()).isEmpty();
    }

    @Test
    void aScanOnAnotherLocalDayIsOutOfDate() {
        // 23:30 en Bogotá del día anterior (04:30Z del día válido) no es el día válido.
        Instant lateYesterday = Instant.parse("2026-11-07T04:30:00Z");
        SyncDecision decision = SyncConflictResolver.decide(facts(lateYesterday, true, "ISSUED", "CONFIRMED", DAY, false));

        assertThat(decision.serverOutcome()).isEqualTo("REJECTED_OUT_OF_DATE");
        assertThat(decision.status()).isEqualTo("REJECTED");
        assertThat(decision.incidentType()).isNull();
        assertThat(decision.effects()).containsExactly(Effect.PERSIST_EVIDENCE);
    }

    @Test
    void theLocalDayIsBogotaNotUtc() {
        // 02:00Z del 8 de noviembre son las 21:00 del 7 en Bogotá: sigue siendo el día válido.
        SyncDecision decision = SyncConflictResolver.decide(new SyncFacts(Instant.parse("2026-11-08T03:00:00Z"),
                Instant.parse("2026-11-08T02:00:00Z"), true, "ISSUED", "CONFIRMED", DAY, false));

        assertThat(decision.serverOutcome()).isEqualTo("ACCEPTED");
    }

    @Test
    void anExpiredVoucherIsOutOfDate() {
        SyncDecision decision = SyncConflictResolver.decide(facts(SCANNED, true, "EXPIRED", "CONFIRMED", DAY, false));

        assertThat(decision.serverOutcome()).isEqualTo("REJECTED_OUT_OF_DATE");
    }

    @ParameterizedTest
    @EnumSource(value = Cancelled.class)
    void aRevokedVoucherOrCancelledBookingProtectsTheCommunityShare(Cancelled cancelled) {
        SyncDecision decision = SyncConflictResolver.decide(
                facts(SCANNED, true, cancelled.voucher, cancelled.booking, DAY, false));

        assertThat(decision.serverOutcome()).isEqualTo("REJECTED_REVOKED");
        assertThat(decision.status()).isEqualTo("CONFLICT");
        assertThat(decision.incidentType()).isEqualTo("OVER_REDEMPTION_COMMUNITY_PROTECTED");
        assertThat(decision.incidentSeverity()).isEqualTo("HIGH");
        assertThat(decision.effects()).containsExactlyInAnyOrder(Effect.PERSIST_EVIDENCE, Effect.MARK_SYNC_CONFLICT,
                Effect.PROTECT_COMMUNITY_SHARE);
    }

    @Test
    void aSecondScanOfAnAlreadyRedeemedVoucherIsADuplicateWithoutStateChanges() {
        SyncDecision decision = SyncConflictResolver.decide(facts(SCANNED, true, "CONSUMED", "EXECUTED", DAY, true));

        assertThat(decision.serverOutcome()).isEqualTo("REJECTED_DUPLICATE");
        assertThat(decision.status()).isEqualTo("CONFLICT");
        assertThat(decision.incidentType()).isEqualTo("DUPLICATE_OFFLINE_REDEMPTION");
        assertThat(decision.incidentSeverity()).isEqualTo("MEDIUM");
        assertThat(decision.effects()).containsExactly(Effect.PERSIST_EVIDENCE);
    }

    @Test
    void theRevocationRuleWinsOverTheDuplicateRule() {
        // Revocado y además ya canjeado: el 85 % comunitario manda (regla 4 antes que la 5).
        SyncDecision decision = SyncConflictResolver.decide(facts(SCANNED, true, "REVOKED", "CANCELLED_REFUNDED", DAY, true));

        assertThat(decision.serverOutcome()).isEqualTo("REJECTED_REVOKED");
    }

    @Test
    void theClockSkewRuleIsEvaluatedBeforeEverythingElse() {
        SyncDecision decision = SyncConflictResolver.decide(
                facts(RECEIVED.plus(Duration.ofHours(2)), true, "REVOKED", "CANCELLED_REFUNDED", DAY, true));

        assertThat(decision.serverOutcome()).isEqualTo("REJECTED_CLOCK_SKEW");
    }

    @Test
    void noDecisionCanRefundOrRevertTheCommunityShare() {
        // Garantía estructural (ADR-0033 §3): el catálogo de efectos no tiene reembolso ni reversión del 85 %.
        assertThat(EnumSet.allOf(Effect.class)).containsExactlyInAnyOrder(Effect.PERSIST_EVIDENCE, Effect.REDEEM,
                Effect.MARK_SYNC_CONFLICT, Effect.PROTECT_COMMUNITY_SHARE);
        Set<Effect> allowedOnRevoked = EnumSet.of(Effect.PERSIST_EVIDENCE, Effect.MARK_SYNC_CONFLICT,
                Effect.PROTECT_COMMUNITY_SHARE);
        for (Cancelled cancelled : Cancelled.values()) {
            SyncDecision decision = SyncConflictResolver.decide(
                    facts(SCANNED, true, cancelled.voucher, cancelled.booking, DAY, false));
            assertThat(decision.effects()).isSubsetOf(allowedOnRevoked);
        }
    }

    /** Combinaciones que el contrato trata como «voucher revocado o reserva cancelada» (regla 4). */
    enum Cancelled {
        REVOKED_VOUCHER("REVOKED", "CONFIRMED"),
        CANCELLED_REFUNDED("REVOKED", "CANCELLED_REFUNDED"),
        CANCELLED_NO_REFUND("REVOKED", "CANCELLED_NO_REFUND"),
        CANCELLED_BY_PLATFORM("REVOKED", "CANCELLED_BY_PLATFORM"),
        ALREADY_IN_CONFLICT("REVOKED", "SYNC_CONFLICT"),
        ISSUED_BUT_CANCELLED("ISSUED", "CANCELLED_REFUNDED");

        final String voucher;
        final String booking;

        Cancelled(String voucher, String booking) {
            this.voucher = voucher;
            this.booking = booking;
        }
    }
}
