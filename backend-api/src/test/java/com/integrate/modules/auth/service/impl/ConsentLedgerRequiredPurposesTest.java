package com.integrate.modules.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.integrate.modules.auth.entity.ConsentPurpose;
import com.integrate.modules.auth.entity.RoleCode;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** ADR-0068: {@code SENSITIVE_VICTIM} solo es exigible a un anfitrión que ya tiene constancia de ese consentimiento. */
class ConsentLedgerRequiredPurposesTest {

    private static final Set<ConsentPurpose> NONE = Set.of();
    private static final Set<ConsentPurpose> VICTIM_RECORD = Set.of(ConsentPurpose.SENSITIVE_VICTIM);

    @Test
    void hostWithoutRecordOnlyNeedsPersonalData() {
        assertThat(ConsentLedger.requiredPurposes(List.of(RoleCode.HOST, RoleCode.TOURIST), NONE))
                .containsExactly(ConsentPurpose.PERSONAL_DATA);
    }

    @Test
    void hostWithPersonalDataRecordOnlyStillOnlyNeedsPersonalData() {
        assertThat(ConsentLedger.requiredPurposes(List.of(RoleCode.HOST), Set.of(ConsentPurpose.PERSONAL_DATA)))
                .containsExactly(ConsentPurpose.PERSONAL_DATA);
    }

    @Test
    void hostWithVictimRecordKeepsBothPurposes() {
        assertThat(ConsentLedger.requiredPurposes(List.of(RoleCode.HOST, RoleCode.TOURIST), VICTIM_RECORD))
                .containsExactlyInAnyOrder(ConsentPurpose.PERSONAL_DATA, ConsentPurpose.SENSITIVE_VICTIM);
    }

    @Test
    void victimRecordDoesNotMakeItRequiredForRolesThatAreNotHost() {
        for (RoleCode role : List.of(RoleCode.TOURIST, RoleCode.LINK_MANAGER, RoleCode.ADMIN)) {
            assertThat(ConsentLedger.requiredPurposes(List.of(role), VICTIM_RECORD))
                    .as("rol %s", role)
                    .containsExactly(ConsentPurpose.PERSONAL_DATA);
        }
    }

    @Test
    void healthEmergencyIsNeverRequiredAtLogin() {
        assertThat(ConsentLedger.requiredPurposes(List.of(RoleCode.HOST),
                Set.of(ConsentPurpose.SENSITIVE_VICTIM, ConsentPurpose.HEALTH_EMERGENCY)))
                .doesNotContain(ConsentPurpose.HEALTH_EMERGENCY);
    }
}
