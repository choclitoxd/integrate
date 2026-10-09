package com.integrate.modules.voucher.service.impl;

import com.integrate.modules.booking.service.model.VoucherSubject;
import com.integrate.modules.catalog.service.model.ScheduleSummary;
import com.integrate.modules.voucher.entity.Voucher;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Claims mínimos del voucher (ADR-0010 §2): solo lo indispensable para validar en campo. Sin nombre, EPS, contacto
 * ni montos (Ley 1581, minimización). El orden de {@link #asOrderedMap()} es parte del formato: cambiarlo impide
 * re-derivar los tokens ya emitidos.
 *
 * @param validDate fecha local de la franja ({@code America/Bogota}): la PWA rechaza el QR en otra fecha
 * @param doc       documento del titular como {@code TIPO:NÚMERO}
 */
record VoucherClaims(UUID jti, UUID bookingId, UUID scheduleId, UUID experienceId, LocalDate validDate, String doc,
                     int slots, long issuedAt, long expiresAt) {

    static final String ISSUER = "integrate";

    static VoucherClaims of(Voucher voucher, VoucherSubject subject, ScheduleSummary schedule) {
        return new VoucherClaims(voucher.getJti(), subject.bookingId(), schedule.scheduleId(), schedule.experienceId(),
                validDate(schedule), subject.holder().docType() + ":" + subject.holder().docNumber(), subject.slots(),
                voucher.getCreatedAt().getEpochSecond(), voucher.getExpiresAt().getEpochSecond());
    }

    static LocalDate validDate(ScheduleSummary schedule) {
        return schedule.startTime().atZone(ZoneId.of(schedule.timezone())).toLocalDate();
    }

    Map<String, Object> asOrderedMap() {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", ISSUER);
        claims.put("jti", jti.toString());
        claims.put("bid", bookingId.toString());
        claims.put("sid", scheduleId.toString());
        claims.put("xid", experienceId.toString());
        claims.put("vd", validDate.toString());
        claims.put("doc", doc);
        claims.put("slots", slots);
        claims.put("iat", issuedAt);
        claims.put("exp", expiresAt);
        return claims;
    }
}
