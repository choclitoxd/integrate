package com.integrate.modules.payment.service.impl;

import com.integrate.modules.payment.service.impl.MoneyRules.SettlementBreakdown;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Component;

/**
 * Comprobante contable de un lote de dispersión (RF-19, ADR-0034 §6): una página con el desglose. Fuente TrueType
 * incrustada (Liberation Sans, incluida en PDFBox) para los acentos; no pretende ser PDF/A.
 */
@Component
class ReceiptPdfRenderer {

    static final String CONTENT_TYPE = "application/pdf";
    private static final String FONT_RESOURCE = "/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf";
    private static final Locale ES_CO = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' uuuu, HH:mm", ES_CO);
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    byte[] render(UUID batchId, String associationName, SettlementBreakdown b, int payments, Instant paidAt) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDType0Font font;
            try (InputStream in = PDDocument.class.getResourceAsStream(FONT_RESOURCE)) {
                font = PDType0Font.load(document, in);
            }
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float y = 780;
                y = line(content, font, 18, y, "Intégrate · Comprobante de dispersión");
                y = line(content, font, 11, y - 6, "Turismo comunitario de memoria y paz");
                y = line(content, font, 12, y - 24, "Asociación beneficiaria: " + associationName);
                y = line(content, font, 12, y - 4, "Lote: " + batchId);
                y = line(content, font, 12, y - 4, "Pagado el: " + DATE.format(paidAt.atZone(BOGOTA))
                        + " (hora de Colombia)");
                y = line(content, font, 12, y - 4, "Pagos incluidos: " + payments);
                y = line(content, font, 13, y - 24, "Valor bruto cobrado: " + cop(b.grossCop()));
                y = line(content, font, 13, y - 4, "Comisión de la plataforma (15 %): " + cop(b.platformFeeCop()));
                y = line(content, font, 11, y - 2, "   de la cual, comisión de la pasarela: " + cop(b.gatewayFeeCop()));
                y = line(content, font, 14, y - 14, "Desembolso neto a la comunidad (85 %): "
                        + cop(b.netCommunityCop()));
                line(content, font, 9, y - 36, "Documento contable generado automáticamente. Bruto = neto comunitario + "
                        + "comisión de la plataforma. Valores en pesos colombianos.");
            }
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static float line(PDPageContentStream content, PDType0Font font, float size, float y, String text)
            throws IOException {
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(50, y);
        content.showText(text);
        content.endText();
        return y - size * 1.5f;
    }

    /** {@code $ 160.000} (pesos sin centavos, separador de miles con punto). */
    static String cop(java.math.BigDecimal amount) {
        NumberFormat format = NumberFormat.getIntegerInstance(ES_CO);
        format.setGroupingUsed(true);
        return "$ " + format.format(amount.longValueExact()).replace(' ', '.').replace(',', '.');
    }
}