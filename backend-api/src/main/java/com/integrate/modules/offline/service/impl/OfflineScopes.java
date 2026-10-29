package com.integrate.modules.offline.service.impl;

import com.integrate.common.exception.BusinessRuleException;
import com.integrate.common.exception.ErrorCode;
import com.integrate.modules.offline.repository.OfflineQueries;
import com.integrate.modules.offline.repository.OfflineQueries.DeviceRow;
import com.integrate.modules.offline.repository.OfflineQueries.ManagerScope;
import java.time.Clock;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Quién actúa y desde qué dispositivo (ADR-0032 §1): el gestor debe tener una acreditación {@code ACTIVE} y el
 * dispositivo se registra en su primer uso. Sin transacción propia.
 */
@Component
@RequiredArgsConstructor
class OfflineScopes {

    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z0-9._-]{4,100}$");

    private final OfflineQueries queries;
    private final Clock clock;

    ManagerScope requireManager(UUID userId) {
        return queries.activeManager(userId)
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.ENROLLMENT_MANAGER_NOT_ACTIVE));
    }

    /** Registra el dispositivo si es nuevo y actualiza {@code last_seen_at}. */
    UUID resolveDevice(ManagerScope manager, String identifier, String userAgent) {
        if (identifier == null || !IDENTIFIER.matcher(identifier).matches()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "X-Device-Id no es un identificador válido");
        }
        DeviceRow device = queries.findDevice(identifier).orElse(null);
        if (device == null) {
            queries.insertDeviceIfAbsent(identifier, manager.managerId(), platformOf(userAgent));
            device = queries.findDevice(identifier).orElseThrow();
        }
        if (!device.managerId().equals(manager.managerId())) {
            throw new BusinessRuleException(ErrorCode.OFFLINE_DEVICE_OWNED_BY_OTHER);
        }
        if (!"ACTIVE".equals(device.status())) {
            throw new BusinessRuleException(ErrorCode.OFFLINE_DEVICE_NOT_AUTHORIZED);
        }
        queries.touchDevice(device.id(), clock.instant());
        return device.id();
    }

    private static String platformOf(String userAgent) {
        String ua = userAgent == null ? "" : userAgent.toLowerCase(Locale.ROOT);
        if (ua.contains("android")) {
            return "ANDROID";
        }
        if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ios")) {
            return "IOS";
        }
        return "OTHER";
    }
}
