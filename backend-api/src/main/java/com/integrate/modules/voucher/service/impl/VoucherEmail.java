package com.integrate.modules.voucher.service.impl;

import com.integrate.modules.auth.service.AssistedAccountService;
import com.integrate.modules.auth.service.UserDirectory;
import com.integrate.modules.auth.service.UserDirectory.UserSummary;
import com.integrate.modules.booking.service.model.VoucherSubject;
import com.integrate.modules.catalog.service.model.ScheduleSummary;
import com.integrate.modules.notification.service.NotificationOutbox;
import com.integrate.modules.notification.service.model.EmailRequest;
import com.integrate.modules.notification.service.model.EmailRequest.Attachment;
import com.integrate.modules.voucher.entity.Voucher;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Correo {@code VOUCHER_ISSUED} (ADR-0011 §2): se encola en la transacción que fija el PDF del voucher, con el PDF
 * adjunto y el resumen de la reserva. Nunca lleva EPS ni contacto de emergencia. Los correos sintéticos de los
 * anfitriones enrolados no se encolan: nadie los recibiría.
 */
@Component
@RequiredArgsConstructor
class VoucherEmail {

    static final String EVENT = "VOUCHER_ISSUED";
    static final String TEMPLATE = "voucher-issued";
    private static final Logger log = LoggerFactory.getLogger(VoucherEmail.class);
    private static final Locale ES_CO = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' uuuu", ES_CO);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final UserDirectory userDirectory;
    private final NotificationOutbox outbox;

    /** @return id del mensaje encolado, o vacío si el turista no tiene un correo entregable */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<UUID> enqueue(Voucher voucher, String pdfKey, VoucherSubject subject, ScheduleSummary schedule) {
        UserSummary tourist = userDirectory.findSummaries(Set.of(subject.touristId())).get(subject.touristId());
        if (tourist == null || tourist.email() == null
                || tourist.email().toLowerCase(Locale.ROOT).endsWith("@" + AssistedAccountService.SYNTHETIC_EMAIL_DOMAIN)) {
            log.info("voucher.email skipped (no deliverable address) bookingId={}", subject.bookingId());
            return Optional.empty();
        }
        ZoneId zone = ZoneId.of(schedule.timezone());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("touristName", tourist.fullName());
        payload.put("experienceTitle", schedule.title());
        payload.put("localDate", capitalize(DATE.format(schedule.startTime().atZone(zone))));
        payload.put("localStartTime", TIME.format(schedule.startTime().atZone(zone)));
        payload.put("localEndTime", TIME.format(schedule.endTime().atZone(zone)));
        payload.put("meetingPoint", schedule.meetingPoint());
        payload.put("municipality", schedule.municipality() == null ? "" : schedule.municipality()
                + (schedule.department() == null ? "" : ", " + schedule.department()));
        payload.put("holderName", subject.holder().fullName());
        payload.put("slots", subject.slots());
        payload.put("totalAmountCop", cop(subject.totalAmountCop().longValueExact()));
        payload.put("shortCode", voucher.getShortCode());
        payload.put("bookingId", subject.bookingId().toString());

        return Optional.of(outbox.enqueueEmail(new EmailRequest("voucher", voucher.getId().toString(), EVENT,
                subject.touristId(), tourist.email(), TEMPLATE, payload,
                List.of(new Attachment(Attachment.VOUCHERS, pdfKey, "voucher-" + voucher.getShortCode() + ".pdf",
                        VoucherPdfRenderer.CONTENT_TYPE)))));
    }

    /** {@code $ 160.000} (pesos sin centavos, separador de miles con punto). */
    static String cop(long amount) {
        NumberFormat format = NumberFormat.getIntegerInstance(ES_CO);
        format.setGroupingUsed(true);
        return "$ " + format.format(amount).replace(' ', '.').replace(',', '.');
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
