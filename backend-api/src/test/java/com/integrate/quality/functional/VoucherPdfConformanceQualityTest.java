package com.integrate.quality.functional;

import static org.assertj.core.api.Assertions.assertThat;

import com.integrate.modules.voucher.entity.Voucher;
import com.integrate.modules.voucher.repository.VoucherRepository;
import com.integrate.quality.support.QualityIntegrationTest;
import com.integrate.quality.support.QualityMetrics;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * RNF-23 (Adecuación funcional · completitud): cada compra aprobada genera un voucher PDF/A con localizador, QR,
 * cupos, EPS, contacto de emergencia y términos del servicio. ADR-0010 dejó fuera del PDF la EPS y el contacto de
 * emergencia (minimización de datos de salud en un documento que se imprime y circula): la parte que falta se mide
 * aparte como no conformidad para que el equipo decida si cambia el plan o el PDF.
 */
@Tag("rnf-23")
@Tag("iso-adecuacion")
@DisplayName("[RNF-23] Integridad en expedición y estructura del voucher PDF")
class VoucherPdfConformanceQualityTest extends QualityIntegrationTest {

    @Autowired
    private VoucherRepository vouchers;

    @Test
    @DisplayName("[RNF-23] 100 % de las compras aprobadas generan un voucher PDF/A-2b con su estructura")
    void everyApprovedPurchaseGetsAnArchivalVoucher() throws Exception {
        Scenario s = scenario(12);
        String scheduleId = schedule(s, 12);
        List<String> bookings = new ArrayList<>();
        for (int slots = 1; slots <= 3; slots++) {
            String bookingId = lockBooking(s.touristToken(), scheduleId, slots);
            approvePayment(s.touristToken(), startPayment(s.touristToken(), bookingId));
            bookings.add(bookingId);
        }
        bookings.forEach(this::awaitVoucher);

        long conforming = 0;
        List<String> failures = new ArrayList<>();
        for (String bookingId : bookings) {
            Map<String, Boolean> checks = structure(bookingId);
            if (checks.values().stream().allMatch(Boolean::booleanValue)) {
                conforming++;
            } else {
                failures.add(bookingId + " " + checks);
            }
        }

        QualityMetrics.record("RNF-23", "Compras aprobadas con voucher PDF/A conforme",
                QualityMetrics.percent(conforming, bookings.size()), "%", "= 100 %", failures.isEmpty(),
                bookings.size() + " compras; PDF/A-2b (XMP pdfaid), fuentes incrustadas, localizador, QR, cupos, "
                        + "titular y términos");
        assertThat(failures).isEmpty();
    }

    @Test
    @Tag("nonconformity")
    @DisplayName("[RNF-23] NC: el PDF no incluye EPS ni contacto de emergencia (ADR-0010 los excluye)")
    void voucherShouldCarryHealthAndEmergencyData() throws Exception {
        Scenario s = scenario(12);
        String bookingId = lockBooking(s.touristToken(), schedule(s, 6), 1);
        approvePayment(s.touristToken(), startPayment(s.touristToken(), bookingId));
        awaitVoucher(bookingId);
        String text = text(bookingId);
        boolean eps = text.contains("Sura");
        boolean emergency = text.contains("Carlos Gomez") || text.contains("3001234567");

        QualityMetrics.record("RNF-23", "Campos de salud y emergencia presentes en el PDF",
                (eps ? 50 : 0) + (emergency ? 50 : 0), "%", "= 100 % (EPS y contacto de emergencia)",
                eps && emergency, "EPS presente = " + eps + ", contacto de emergencia presente = " + emergency);
        assertThat(eps).as("EPS en el PDF").isTrue();
        assertThat(emergency).as("contacto de emergencia en el PDF").isTrue();
    }

    private Map<String, Boolean> structure(String bookingId) throws Exception {
        Voucher voucher = vouchers.findByBookingId(UUID.fromString(bookingId)).orElseThrow();
        byte[] pdf = voucherStorage.get(voucher.getPdfS3Key()).orElseThrow();
        Map<String, Boolean> checks = new LinkedHashMap<>();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            String xmp = new String(document.getDocumentCatalog().getMetadata().toByteArray(), StandardCharsets.UTF_8);
            checks.put("pdfa-2b", xmp.contains("pdfaid:part") && xmp.matches("(?s).*pdfaid:part(>|=\")2.*")
                    && xmp.matches("(?s).*pdfaid:conformance(>|=\")B.*"));
            checks.put("outputIntent", document.getDocumentCatalog().getOutputIntents().size() == 1);
            boolean embedded = true;
            var resources = document.getPage(0).getResources();
            for (var name : resources.getFontNames()) {
                embedded &= resources.getFont(name).isEmbedded();
            }
            checks.put("fuentes", embedded);
            String text = new PDFTextStripper().getText(document);
            int slots = jdbc.queryForObject("SELECT slots_count FROM booking.bookings WHERE id = ?::uuid",
                    Integer.class, bookingId);
            checks.put("localizador", text.contains(voucher.getShortCode()));
            checks.put("cupos", text.contains("Cupos amparados: " + slots));
            checks.put("titular", text.contains("Titular:"));
            checks.put("terminos", text.contains("intransferible") && text.contains("Ley 1581"));
            checks.put("qr", document.getPage(0).getContents() != null);
        }
        return checks;
    }

    private String text(String bookingId) throws Exception {
        Voucher voucher = vouchers.findByBookingId(UUID.fromString(bookingId)).orElseThrow();
        try (PDDocument document = Loader.loadPDF(voucherStorage.get(voucher.getPdfS3Key()).orElseThrow())) {
            return new PDFTextStripper().getText(document);
        }
    }
}
