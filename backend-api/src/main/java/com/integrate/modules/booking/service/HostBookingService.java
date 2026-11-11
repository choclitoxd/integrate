package com.integrate.modules.booking.service;

import com.integrate.common.payload.PageResponse;
import com.integrate.modules.auth.service.model.ClientInfo;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public interface HostBookingService {
    record Summary(UUID bookingId, String experienceTitle, Instant startsAt, String holderName,
                   UUID scheduleId, int slotsCount, long scheduleBookings, long scheduleSlots) { }
    record HolderEps(String medicalEps) {
        @Override public String toString() { return "HolderEps[redacted]"; }
    }
    enum Period { UPCOMING, HISTORY }
    record Filter(Period period, LocalDate date, String experience) { }

    PageResponse<Summary> list(UUID hostId, int page, int size, Filter filter);
    HolderEps holderEps(UUID hostId, UUID bookingId, ClientInfo client);
}
