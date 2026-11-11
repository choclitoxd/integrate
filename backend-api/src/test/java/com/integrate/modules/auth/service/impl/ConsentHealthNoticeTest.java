package com.integrate.modules.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.integrate.common.exception.BusinessRuleException;
import com.integrate.modules.auth.dto.request.ConsentAcceptance;
import com.integrate.modules.auth.entity.ConsentPurpose;
import com.integrate.modules.auth.entity.PrivacyPolicy;
import com.integrate.modules.auth.entity.RoleCode;
import com.integrate.modules.auth.repository.UserRoleRepository;
import com.integrate.modules.auth.service.model.ClientInfo;
import com.integrate.modules.auth.service.model.ConsentStatus;
import com.integrate.modules.auth.service.model.ConsentStatus.PurposeConsent;
import com.integrate.modules.auth.service.model.ConsentStatus.State;
import java.net.InetAddress;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** ADR-0054/0060: el acto de salud solo acredita la EPS cuando registra el aviso aprobado de la política vigente. */
class ConsentHealthNoticeTest {

    private final UUID userId = UUID.randomUUID();
    private final ClientInfo client = new ClientInfo(InetAddress.getLoopbackAddress(), "test");
    private final PrivacyPolicy policy = mock(PrivacyPolicy.class);
    private ConsentLedger ledger;
    private ConsentServiceImpl service;

    @BeforeEach
    void setUp() {
        ledger = mock(ConsentLedger.class);
        UserRoleRepository roles = mock(UserRoleRepository.class);
        when(roles.findActiveRoleCodes(userId)).thenReturn(List.of(RoleCode.TOURIST));
        when(policy.getVersion()).thenReturn("v1");
        when(ledger.requireCurrentPolicy("v1", "policyVersion")).thenReturn(policy);
        when(ledger.currentPolicyLockedForHealth()).thenReturn(Optional.of(policy));
        service = new ConsentServiceImpl(ledger, roles);
    }

    @Test
    void healthNoticeIsRejectedWithoutTheHealthPurpose() {
        ConsentAcceptance acceptance = new ConsentAcceptance("v1", Set.of(ConsentPurpose.PERSONAL_DATA), "h1");

        assertThatThrownBy(() -> service.grant(userId, acceptance, client)).isInstanceOf(BusinessRuleException.class);
        verify(ledger, never()).insertGrants(any(), any(), anySet(), anySet(), any(), any());
    }

    @Test
    void healthGrantRecordsTheApprovedNoticeVersion() {
        when(ledger.status(eq(userId), any(), any())).thenReturn(status(State.MISSING, null));
        ConsentAcceptance acceptance = new ConsentAcceptance("v1", Set.of(ConsentPurpose.HEALTH_EMERGENCY), "h1");

        service.grant(userId, acceptance, client);

        verify(ledger).requireApprovedNotice(policy, "h1", "healthNoticeVersion");
        verify(ledger).insertGrants(eq(userId), eq(policy), eq(acceptance.purposes()), eq(Set.of()), eq(client),
                eq("h1"));
    }

    @Test
    void healthConsentWithoutNoticeIsRenewedWhenTheNoticeIsRequested() {
        when(ledger.status(eq(userId), any(), any())).thenReturn(status(State.VALID, null));
        ConsentAcceptance acceptance = new ConsentAcceptance("v1", Set.of(ConsentPurpose.HEALTH_EMERGENCY), "h1");

        service.grant(userId, acceptance, client);

        ArgumentCaptor<Set<ConsentPurpose>> skipped = ArgumentCaptor.captor();
        verify(ledger).insertGrants(eq(userId), eq(policy), eq(acceptance.purposes()), skipped.capture(), eq(client),
                eq("h1"));
        assertThat(skipped.getValue()).isEmpty();
    }

    @Test
    void healthConsentAlreadyBackedByTheSameNoticeIsNotDuplicated() {
        when(ledger.status(eq(userId), any(), any())).thenReturn(status(State.VALID, "h1"));
        ConsentAcceptance acceptance = new ConsentAcceptance("v1", Set.of(ConsentPurpose.HEALTH_EMERGENCY), "h1");

        service.grant(userId, acceptance, client);

        ArgumentCaptor<Set<ConsentPurpose>> skipped = ArgumentCaptor.captor();
        verify(ledger).insertGrants(eq(userId), eq(policy), eq(acceptance.purposes()), skipped.capture(), eq(client),
                eq("h1"));
        assertThat(skipped.getValue()).containsExactly(ConsentPurpose.HEALTH_EMERGENCY);
    }

    @Test
    void legacyGrantWithoutNoticeKeepsTheExistingBehaviour() {
        when(ledger.status(eq(userId), any(), any())).thenReturn(status(State.MISSING, null));
        ConsentAcceptance acceptance = new ConsentAcceptance("v1", EnumSet.of(ConsentPurpose.HEALTH_EMERGENCY));

        service.grant(userId, acceptance, client);

        verify(ledger, never()).lockHealthConsent(any());
        verify(ledger, never()).requireApprovedNotice(any(), any(), any());
        verify(ledger).insertGrants(eq(userId), eq(policy), eq(acceptance.purposes()), eq(Set.of()), eq(client),
                eq((String) null));
    }

    private static ConsentStatus status(State state, String noticeVersion) {
        return new ConsentStatus("v1", List.of(new PurposeConsent(ConsentPurpose.HEALTH_EMERGENCY, state, "v1",
                state == State.MISSING ? null : Instant.EPOCH, noticeVersion)), List.of());
    }
}
