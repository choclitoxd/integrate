package com.integrate.modules.voucher.service.impl;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent;
import org.apache.xmpbox.XMPMetadata;
import org.apache.xmpbox.schema.AdobePDFSchema;
import org.apache.xmpbox.schema.DublinCoreSchema;
import org.apache.xmpbox.schema.PDFAIdentificationSchema;
import org.apache.xmpbox.schema.XMPBasicSchema;
import org.apache.xmpbox.xml.XmpSerializer;
import org.springframework.stereotype.Component;

/**
 * PDF oficial del voucher (ADR-0010 §6), con objetivo PDF/A-2b: fuente TrueType incrustada (Liberation Sans, incluida
 * en PDFBox con licencia OFL), {@code OutputIntent} sRGB del JRE, metadatos XMP {@code pdfaid} coherentes con el
 * diccionario de información y colores RGB, sin transparencias ni cifrado.
 *
 * <p>El QR se dibuja como vector (un cuadrado por módulo): nítido a cualquier escala de impresión. Corrección de
 * errores {@code L} y margen de 4 módulos para que el token RS256 (~650 caracteres) sea legible con cámaras de gama
 * baja; ocupa 80 mm (módulos de ~0,8 mm).
 */
@Component
class VoucherPdfRenderer {

    static final String FONT_RESOURCE = "/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf";
    static final String CONTENT_TYPE = "application/pdf";
    private static final String PRODUCER = "Intégrate";
    private static final float QR_SIZE = 227f;        // 80 mm
    private static final float MARGIN = 50f;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' uuuu",
            Locale.forLanguageTag("es-CO"));
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm 'h'");

    byte[] render(VoucherDocument voucher) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDType0Font font;
            try (InputStream ttf = VoucherPdfRenderer.class.getResourceAsStream(FONT_RESOURCE)) {
                if (ttf == null) {
                    throw new IllegalStateException("PDFBox font resource is missing: " + FONT_RESOURCE);
                }
                font = PDType0Font.load(document, ttf, true);
            }
            archivalMetadata(document, voucher);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                draw(content, font, page.getMediaBox(), voucher);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Voucher PDF could not be rendered", e);
        }
    }

    private static void draw(PDPageContentStream content, PDType0Font font, PDRectangle box, VoucherDocument v)
            throws IOException {
        ZoneId zone = ZoneId.of(v.timezone());
        float top = box.getHeight() - MARGIN;
        float y = top;
        content.setNonStrokingColor(0.08f, 0.33f, 0.18f);
        y = line(content, font, 20, MARGIN, y, "Voucher oficial · Intégrate");
        content.setNonStrokingColor(0f, 0f, 0f);
        y = line(content, font, 11, MARGIN, y - 4, "Turismo comunitario de memoria y paz");
        y -= 14;

        y = line(content, font, 15, MARGIN, y, v.title());
        y = line(content, font, 11, MARGIN, y - 2, capitalize(DATE.format(v.startTime().atZone(zone)))
                + " · " + TIME.format(v.startTime().atZone(zone)) + " a " + TIME.format(v.endTime().atZone(zone))
                + " (hora de Colombia)");
        y = line(content, font, 11, MARGIN, y, "Punto de encuentro: " + v.meetingPoint());
        if (v.municipality() != null) {
            y = line(content, font, 11, MARGIN, y, v.municipality()
                    + (v.department() == null ? "" : ", " + v.department()));
        }
        y -= 10;
        y = line(content, font, 12, MARGIN, y, "Titular: " + v.holderName() + " · " + v.holderDocument());
        y = line(content, font, 11, MARGIN, y, "Cupos amparados: " + v.slots());
        for (String participant : v.participantNames()) {
            y = line(content, font, 10, MARGIN + 12, y, "• " + participant);
        }

        float qrTop = y - 16;
        drawQr(content, v.token(), MARGIN, qrTop - QR_SIZE, QR_SIZE);
        float textX = MARGIN + QR_SIZE + 24;
        float ty = qrTop - 14;
        ty = line(content, font, 10, textX, ty, "Código de contingencia");
        ty = line(content, font, 22, textX, ty - 4, v.shortCode());
        ty = line(content, font, 9, textX, ty - 8, "Úselo si la cámara no puede leer el QR.");
        ty = line(content, font, 9, textX, ty - 8, "Emitido: " + STAMP.format(v.issuedAt().atZone(zone)));
        ty = line(content, font, 9, textX, ty, "Válido hasta: " + STAMP.format(v.expiresAt().atZone(zone)));
        line(content, font, 7, textX, ty - 6, "Voucher " + v.jti());

        float legalY = qrTop - QR_SIZE - 28;
        for (String text : wrap("Documento personal e intransferible. Presente este voucher y el documento de "
                + "identidad del titular en el punto de encuentro. El código QR está firmado digitalmente por "
                + "Intégrate y se verifica sin conexión; cualquier alteración lo invalida. Cada voucher se puede "
                + "usar una sola vez. Sus datos personales se tratan conforme a la Ley 1581 de 2012.", 105)) {
            legalY = line(content, font, 8, MARGIN, legalY, text);
        }
    }

    /** Un cuadrado negro por módulo activo; el margen de 4 módulos lo aporta la propia matriz. */
    private static void drawQr(PDPageContentStream content, String token, float x, float y, float size)
            throws IOException {
        BitMatrix matrix = qrMatrix(token);
        float module = size / matrix.getWidth();
        content.setNonStrokingColor(1f, 1f, 1f);
        content.addRect(x, y, size, size);
        content.fill();
        content.setNonStrokingColor(0f, 0f, 0f);
        for (int row = 0; row < matrix.getHeight(); row++) {
            for (int col = 0; col < matrix.getWidth(); col++) {
                if (matrix.get(col, row)) {
                    content.addRect(x + col * module, y + size - (row + 1) * module, module, module);
                }
            }
        }
        content.fill();
    }

    static BitMatrix qrMatrix(String token) {
        try {
            return new QRCodeWriter().encode(token, BarcodeFormat.QR_CODE, 0, 0,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L, EncodeHintType.MARGIN, 4,
                            EncodeHintType.CHARACTER_SET, "ISO-8859-1"));
        } catch (WriterException e) {
            throw new IllegalStateException("The voucher token does not fit in a QR code", e);
        }
    }

    private static float line(PDPageContentStream content, PDType0Font font, float size, float x, float y,
                              String text) throws IOException {
        float baseline = y - size;
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(x, baseline);
        content.showText(text);
        content.endText();
        return baseline - size * 0.35f;
    }

    /** PDF/A-2b: intención de salida sRGB y XMP {@code pdfaid} con los mismos datos que el diccionario Info. */
    private static void archivalMetadata(PDDocument document, VoucherDocument v) throws IOException {
        ICC_Profile srgb = ICC_Profile.getInstance(ColorSpace.CS_sRGB);
        PDOutputIntent intent = new PDOutputIntent(document, new ByteArrayInputStream(srgb.getData()));
        intent.setInfo("sRGB IEC61966-2.1");
        intent.setOutputCondition("sRGB IEC61966-2.1");
        intent.setOutputConditionIdentifier("sRGB IEC61966-2.1");
        intent.setRegistryName("http://www.color.org");
        document.getDocumentCatalog().addOutputIntent(intent);

        String title = "Voucher " + v.shortCode() + " · " + v.title();
        Calendar created = calendar(v.issuedAt());
        PDDocumentInformation info = document.getDocumentInformation();
        info.setTitle(title);
        info.setCreator(PRODUCER);
        info.setProducer(PRODUCER);
        info.setCreationDate(created);
        info.setModificationDate(created);

        XMPMetadata xmp = XMPMetadata.createXMPMetadata();
        PDFAIdentificationSchema pdfa = xmp.createAndAddPDFAIdentificationSchema();
        pdfa.setPart(2);
        try {
            pdfa.setConformance("B");
        } catch (org.apache.xmpbox.type.BadFieldValueException e) {
            throw new IllegalStateException("PDF/A conformance level rejected by xmpbox", e);
        }
        DublinCoreSchema dc = xmp.createAndAddDublinCoreSchema();
        dc.setTitle(title);
        dc.addCreator(PRODUCER);
        XMPBasicSchema basic = xmp.createAndAddXMPBasicSchema();
        basic.setCreatorTool(PRODUCER);
        basic.setCreateDate(created);
        basic.setModifyDate(created);
        AdobePDFSchema pdf = xmp.createAndAddAdobePDFSchema();
        pdf.setProducer(PRODUCER);
        ByteArrayOutputStream serialized = new ByteArrayOutputStream();
        try {
            new XmpSerializer().serialize(xmp, serialized, true);
        } catch (javax.xml.transform.TransformerException e) {
            throw new IOException("XMP metadata could not be serialized", e);
        }
        PDMetadata metadata = new PDMetadata(document);
        metadata.importXMPMetadata(serialized.toByteArray());
        document.getDocumentCatalog().setMetadata(metadata);
    }

    private static Calendar calendar(Instant instant) {
        GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        calendar.setTimeInMillis(instant.toEpochMilli());
        return calendar;
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            if (current.length() + word.length() + 1 > width && !current.isEmpty()) {
                lines.add(current.toString());
                current.setLength(0);
            }
            current.append(current.isEmpty() ? "" : " ").append(word);
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    /** Datos visibles del voucher; nunca EPS ni contacto de emergencia. */
    record VoucherDocument(String title, Instant startTime, Instant endTime, String timezone, String meetingPoint,
                           String municipality, String department, String holderName, String holderDocument,
                           int slots, List<String> participantNames, String shortCode, String jti, Instant issuedAt,
                           Instant expiresAt, String token) {
    }
}
