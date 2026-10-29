package com.integrate.modules.offline.service.impl;

import com.integrate.common.audit.AuditEntry;
import com.integrate.common.audit.AuditTrail;
import com.integrate.common.crypto.SensitiveDataCipher;
import com.integrate.common.exception.BusinessRuleException;
import com.integrate.common.exception.ErrorCode;
import com.integrate.modules.offline.dto.response.RevocationsResponse;
import com.integrate.modules.offline.dto.response.RosterResponse;
import com.integrate.modules.offline.repository.OfflineQueries;
import com.integrate.modules.offline.repository.OfflineQueries.ManagerScope;
import com.integrate.modules.offline.repository.OfflineQueries.ParticipantRow;
import com.integrate.modules.offline.repository.OfflineQueries.RevocationRow;
import com.integrate.modules.offline.repository.OfflineQueries.RosterRow;
import com.integrate.modules.offline.repository.OfflineQueries.SigningKeyRow;
import com.integrate.modules.offline.service.RosterService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class RosterServiceImpl implements RosterService {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private final OfflineQueries queries;
    private final OfflineScopes scopes;
    private final SensitiveDataCipher cipher;
    private final AuditTrail audit;
    private final Clock clock;

    @Value("${integrate.offline.roster.max-vouchers:2000}")
    private int maxVouchers;

    @Override
    @Transactional
    public RosterResponse download(UUID userId, String deviceIdentifier, String userAgent, LocalDate date,
                                   UUID scheduleId) {
        ManagerScope manager = scopes.requireManager(userId);
        UUID deviceId = scopes.resolveDevice(manager, deviceIdentifier, userAgent);
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, BOGOTA);
        LocalDate day = date == null ? today : date;
        if (day.isAfter(today.plusDays(1))) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "date no puede pasar de mañana");
        }
        Instant from = day.atStartOfDay(BOGOTA).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(BOGOTA).toInstant();

        List<RosterRow> rows = queries.roster(manager.associationId(), from, to, scheduleId, maxVouchers + 1);
        if (rows.size() > maxVouchers) {
            throw new BusinessRuleException(ErrorCode.OFFLINE_ROSTER_TOO_LARGE,
                    "El padrón supera " + maxVouchers + " vouchers: descárguelo por scheduleId");
        }
        Map<UUID, List<ParticipantRow>> participants = queries.participants(
                rows.stream().map(RosterRow::bookingId).collect(Collectors.toSet()));
        Set<Short> usedKeys = rows.stream().map(RosterRow::signingKeyId).collect(Collectors.toSet());
        List<SigningKeyRow> keys = queries.signingKeys(usedKeys);
        RevocationsResponse revocations = revocationsOf(manager, null, from);

        List<RosterResponse.Voucher> vouchers = rows.stream().map(row -> voucher(row, participants)).toList();
        short activeKey = keys.stream().filter(k -> "ACTIVE".equals(k.status())).map(SigningKeyRow::id).findFirst()
                .orElseGet(() -> keys.get(0).id());
        queries.insertRosterDownload(deviceId, manager.associationId(), day, activeKey, vouchers.size(),
                revocations.watermark());
        audit.record(new AuditEntry("offline", "device", deviceId.toString(), "OFFLINE_ROSTER_DOWNLOADED",
                AuditEntry.Severity.INFO, userId, null, userAgent,
                Map.of("rosterDate", day.toString(), "vouchers", vouchers.size(),
                        "revocations", revocations.items().size())));
        return new RosterResponse(day, now, manager.associationId(),
                keys.stream().map(k -> new RosterResponse.SigningKey(k.kid(), k.algorithm(), k.publicKeyPem(),
                        k.status(), k.validFrom())).toList(),
                vouchers, revocations);
    }

    @Override
    @Transactional
    public RevocationsResponse revocations(UUID userId, String deviceIdentifier, String userAgent, Instant since) {
        ManagerScope manager = scopes.requireManager(userId);
        UUID deviceId = scopes.resolveDevice(manager, deviceIdentifier, userAgent);
        Instant today = LocalDate.ofInstant(clock.instant(), BOGOTA).atStartOfDay(BOGOTA).toInstant();
        RevocationsResponse response = revocationsOf(manager, since, today);
        audit.record(new AuditEntry("offline", "device", deviceId.toString(), "OFFLINE_REVOCATIONS_DOWNLOADED",
                AuditEntry.Severity.INFO, userId, null, userAgent, Map.of("revocations", response.items().size())));
        return response;
    }

    private RevocationsResponse revocationsOf(ManagerScope manager, Instant since, Instant scheduleNotBefore) {
        List<RevocationRow> rows = queries.revocations(manager.associationId(), since, scheduleNotBefore);
        Instant watermark = rows.stream().map(RevocationRow::revokedAt).max(Comparator.naturalOrder()).orElse(since);
        return new RevocationsResponse(rows.stream().map(r -> new RevocationsResponse.Item(r.voucherId(),
                r.shortCode(), r.revokedAt(), r.reason())).toList(), watermark);
    }

    private RosterResponse.Voucher voucher(RosterRow row, Map<UUID, List<ParticipantRow>> participants) {
        String nameAad = OfflineQueries.EMERGENCY_AAD_PREFIX + "emergency_contact_name_enc:" + row.bookingId();
        String phoneAad = OfflineQueries.EMERGENCY_AAD_PREFIX + "emergency_contact_phone_enc:" + row.bookingId();
        RosterResponse.EmergencyContact contact = new RosterResponse.EmergencyContact(
                cipher.decrypt(row.contactNameEnc(), nameAad, row.contactKeyVersion()),
                cipher.decrypt(row.contactPhoneEnc(), phoneAad, row.contactKeyVersion()));
        List<RosterResponse.Participant> people = participants.getOrDefault(row.bookingId(), List.of()).stream()
                .map(p -> new RosterResponse.Participant(p.fullName(), p.docType(), p.docNumber())).toList();
        return new RosterResponse.Voucher(row.voucherId(), row.bookingId(), row.scheduleId(), row.experienceTitle(),
                row.shortCode(), row.tokenSha256(), row.signingKeyKid(), row.holderName(), row.holderDocument(),
                row.slotsCount(), people, contact);
    }
}
