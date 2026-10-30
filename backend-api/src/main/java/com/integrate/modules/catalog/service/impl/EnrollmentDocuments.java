package com.integrate.modules.catalog.service.impl;

import com.integrate.common.storage.DocumentStorage;
import com.integrate.common.storage.DocumentStorage.PresignedUpload;
import com.integrate.common.storage.DocumentStorage.StoredObject;
import com.integrate.modules.catalog.dto.request.DocumentKind;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Reglas de los PDF del enrolamiento (ADR-0007 §5): llave generada por el servidor sin datos personales
 * ({@code <prefijo>{yyyy}/{MM}/{uuid}.pdf}), subida prefirmada de 5 minutos y verificación del objeto antes
 * de aceptarlo como evidencia.
 */
@Component
@RequiredArgsConstructor
class EnrollmentDocuments {

    static final String CONTENT_TYPE = "application/pdf";
    static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;
    static final Duration UPLOAD_TTL = Duration.ofMinutes(5);
    /** Primeros bytes de todo PDF: {@code %PDF-}. */
    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final Pattern KEY_SUFFIX =
            Pattern.compile("^\\d{4}/\\d{2}/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.pdf$");

    private final DocumentStorage storage;
    private final Clock clock;

    String newKey(DocumentKind kind) {
        ZonedDateTime now = clock.instant().atZone(ZoneOffset.UTC);
        return "%s%04d/%02d/%s.pdf".formatted(kind.prefix(), now.getYear(), now.getMonthValue(), UUID.randomUUID());
    }

    PresignedUpload presign(String key) {
        return storage.presignUpload(key, CONTENT_TYPE, UPLOAD_TTL);
    }

    /**
     * La llave tiene el prefijo de {@code kind} y el objeto existe, es PDF y pesa 5 MB o menos.
     *
     * @throws com.integrate.common.exception.BusinessRuleException {@code ENROLLMENT_DOCUMENT_INVALID} sobre {@code field}
     */
    void requireValid(DocumentKind kind, String key, String field) {
        if (!key.startsWith(kind.prefix()) || !KEY_SUFFIX.matcher(key.substring(kind.prefix().length())).matches()) {
            throw CatalogErrors.documentInvalid(field, "must be a key issued for " + kind);
        }
        Optional<StoredObject> stored = storage.head(key);
        if (stored.isEmpty()) {
            throw CatalogErrors.documentInvalid(field, "the document has not been uploaded");
        }
        if (!CONTENT_TYPE.equalsIgnoreCase(stored.get().contentType())) {
            throw CatalogErrors.documentInvalid(field, "must be a PDF");
        }
        if (stored.get().sizeBytes() <= 0 || stored.get().sizeBytes() > MAX_SIZE_BYTES) {
            throw CatalogErrors.documentInvalid(field, "must be between 1 byte and 5 MB");
        }
        // El tipo lo declara quien sube el archivo: se comprueba también la firma del formato (ADR-0038 §3).
        byte[] prefix = storage.readPrefix(key, PDF_SIGNATURE.length).orElse(new byte[0]);
        if (!Arrays.equals(Arrays.copyOf(prefix, PDF_SIGNATURE.length), PDF_SIGNATURE)) {
            throw CatalogErrors.documentInvalid(field, "must be a PDF");
        }
    }
}
