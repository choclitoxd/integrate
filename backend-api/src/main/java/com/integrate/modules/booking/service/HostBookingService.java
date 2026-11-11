package com.integrate.modules.booking.service;

import com.integrate.common.payload.PageResponse;
import com.integrate.modules.auth.service.model.ClientInfo;
import java.time.Instant;
import java.util.UUID;

public interface HostBookingService {
    record Summary(UUID bookingId, String experienceTitle, Instant startsAt, String holderName) { }
    record HolderEps(String medicalEps) {
        @Override public String toString() { return "HolderEps[redacted]"; }
    }
    PageResponse<Summary> list(UUID hostId, int page, int size);
    HolderEps holderEps(UUID hostId, UUID bookingId, ClientInfo client);
}
