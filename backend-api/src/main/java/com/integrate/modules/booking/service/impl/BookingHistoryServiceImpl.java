package com.integrate.modules.booking.service.impl;

import com.integrate.common.payload.PageResponse;
import com.integrate.modules.booking.dto.response.MyBookingSummary;
import com.integrate.modules.booking.entity.BookingStatus;
import com.integrate.modules.booking.repository.BookingRepository;
import com.integrate.modules.booking.repository.BookingRepository.BookingHistoryRow;
import com.integrate.modules.booking.service.BookingHistoryService;
import com.integrate.modules.catalog.service.ScheduleDirectory;
import com.integrate.modules.catalog.service.model.ScheduleSummary;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Una consulta paginada y un lote al catálogo por página (ADR-0023 §1): sin N+1. */
@Service
@RequiredArgsConstructor
public class BookingHistoryServiceImpl implements BookingHistoryService {

    private final BookingRepository bookingRepository;
    private final ScheduleDirectory scheduleDirectory;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MyBookingSummary> list(UUID touristId, Scope scope, int page, int size) {
        // Sin Sort: el orden lo fija la consulta (fecha de la franja), no el cliente.
        PageRequest pageable = PageRequest.of(page, size);
        Page<BookingHistoryRow> rows = scope == Scope.UPCOMING
                ? bookingRepository.findUpcoming(touristId, clock.instant(), pageable)
                : bookingRepository.findPast(touristId, clock.instant(), pageable);
        Map<UUID, ScheduleSummary> schedules = scheduleDirectory.describeAll(
                rows.getContent().stream().map(BookingHistoryRow::getScheduleId).distinct().toList());
        List<MyBookingSummary> items = rows.getContent().stream().map(row -> {
            ScheduleSummary schedule = schedules.get(row.getScheduleId());
            if (schedule == null) {
                throw new IllegalStateException("Booking " + row.getId() + " has no schedule");
            }
            BookingStatus status = BookingStatus.valueOf(row.getStatus());
            return new MyBookingSummary(row.getId(), status, schedule.experienceId(), schedule.title(),
                    schedule.municipality(), schedule.startTime(), schedule.endTime(), schedule.timezone(),
                    row.getSlots(), row.getTotalAmountCop().longValueExact(), MyBookingSummary.hasVoucher(status));
        }).toList();
        return PageResponse.from(rows, items);
    }
}
