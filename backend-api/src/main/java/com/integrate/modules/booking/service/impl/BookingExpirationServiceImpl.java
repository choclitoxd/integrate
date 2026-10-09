package com.integrate.modules.booking.service.impl;

import com.integrate.common.audit.AuditEntry;
import com.integrate.common.audit.AuditEntry.Severity;
import com.integrate.common.audit.AuditTrail;
import com.integrate.common.persistence.StatusReasonContext;
import com.integrate.modules.booking.entity.Booking;
import com.integrate.modules.booking.repository.BookingRepository;
import com.integrate.modules.booking.service.BookingExpirationService;
import com.integrate.modules.booking.service.model.ExpiredBatch;
import com.integrate.modules.catalog.service.SlotInventory;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Un lote por transacción (ADR-0008 §7): toma las reservas vencidas con {@code SKIP LOCKED}, las pasa a
 * {@code EXPIRED} y restituye sus cupos agregados por franja. Orden de bloqueos: reserva → franja, el mismo que
 * usará el webhook de pago; el lock nunca bloquea reservas existentes, así que no hay ciclo posible.
 */
@Service
@RequiredArgsConstructor
public class BookingExpirationServiceImpl implements BookingExpirationService {

    static final String REASON = "TTL_EXPIRED";

    private final BookingRepository bookingRepository;
    private final SlotInventory slotInventory;
    private final StatusReasonContext statusReason;
    private final AuditTrail auditTrail;
    private final Clock clock;

    @Override
    @Transactional
    public ExpiredBatch expireBatch(int limit) {
        List<Booking> expired = bookingRepository.lockExpiredPending(clock.instant(), limit);
        if (expired.isEmpty()) {
            return ExpiredBatch.EMPTY;
        }
        // Sin usuario autenticado el historial queda con changed_by = NULL (proceso del sistema).
        statusReason.set(REASON);
        expired.forEach(Booking::expire);
        bookingRepository.flush();

        Map<UUID, Integer> slotsBySchedule = expired.stream().collect(Collectors.groupingBy(
                Booking::getScheduleId, Collectors.summingInt(Booking::getSlotsCount)));
        slotInventory.release(slotsBySchedule);

        for (Booking booking : expired) {
            auditTrail.record(new AuditEntry("booking", "bookings", booking.getId().toString(), "BOOKING_EXPIRED",
                    Severity.INFO, null, null, null, Map.of("scheduleId", booking.getScheduleId().toString(),
                    "releasedSlots", (int) booking.getSlotsCount(), "reason", REASON)));
        }
        return new ExpiredBatch(expired.size(), slotsBySchedule.values().stream().mapToInt(Integer::intValue).sum());
    }
}
