package com.integrate.modules.voucher.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;

class VoucherPdfRendererTest {
    @Test
    void keepsMaximumLengthContentWithinPageMargins() throws Exception {
        Instant start = Instant.parse("2026-11-10T15:00:00Z");
        String name = "W".repeat(150);
        var voucher = new VoucherPdfRenderer.VoucherDocument("W".repeat(200), start, start.plusSeconds(3600),
                "America/Bogota", "W".repeat(500), "Municipio", "Departamento", name, "CC 12345678901234567890",
                10, java.util.Collections.nCopies(10, name), "ABCDEFGH", "voucher-id", start,
                start.plusSeconds(86400), "token");
        try (var pdf = Loader.loadPDF(new VoucherPdfRenderer().render(voucher))) {
            assertThat(pdf.getNumberOfPages()).isGreaterThan(1);
            var stripper = new PDFTextStripper() {
                @Override
                protected void processTextPosition(TextPosition position) {
                    assertThat(position.getXDirAdj()).isGreaterThanOrEqualTo(49f);
                    assertThat(position.getXDirAdj() + position.getWidthDirAdj()).isLessThanOrEqualTo(546f);
                    assertThat(position.getYDirAdj()).isLessThanOrEqualTo(792f);
                    super.processTextPosition(position);
                }
            };
            String text = stripper.getText(pdf).replaceAll("\\s+", " ");
            assertThat(text).contains("Puedes compartir tu EPS", "Ley 1581 de 2012");
        }
    }

    @Test
    void explainsVoluntaryHealthInformationWithoutPrintingSensitiveValues() throws Exception {
        Instant start = Instant.parse("2026-11-10T15:00:00Z");
        var voucher = new VoucherPdfRenderer.VoucherDocument("Recorrido", start, start.plusSeconds(3600),
                "America/Bogota", "Parque", "Bogotá", null, "Ana Pérez", "CC 123", 1,
                List.of("Ana Pérez"), "ABCDEFGH", "voucher-id", start, start.plusSeconds(86400), "token");

        try (var pdf = Loader.loadPDF(new VoucherPdfRenderer().render(voucher))) {
            String text = new PDFTextStripper().getText(pdf).replaceAll("\\s+", " ");
            assertThat(text).contains("Puedes compartir tu EPS para facilitar la atención en caso de emergencia. "
                    + "El anfitrión de tu experiencia podrá consultarla durante una emergencia. "
                    + "Es voluntario y puedes reservar sin proporcionarla.");
            assertThat(text).doesNotContain("póliza", "contacto de emergencia", "certificado médico");
            assertThat(pdf.getNumberOfPages()).isEqualTo(1);
        }
    }
}
