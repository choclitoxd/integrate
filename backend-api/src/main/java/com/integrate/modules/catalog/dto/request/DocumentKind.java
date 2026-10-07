package com.integrate.modules.catalog.dto.request;

/**
 * Documentos que el gestor sube a S3 antes del registro asistido (ADR-0007 §5). Cada tipo tiene su
 * prefijo: una llave solo es válida en el campo que le corresponde.
 */
public enum DocumentKind {

    /** Formato físico de consentimiento firmado por el titular ({@code consent.physicalConsentFormS3Key}). */
    PHYSICAL_CONSENT_FORM("consents/physical/"),
    /** Soporte de la condición de víctima ({@code victimData.supportDocS3Key}). */
    VICTIM_SUPPORT_DOC("host-support/");

    private final String prefix;

    DocumentKind(String prefix) {
        this.prefix = prefix;
    }

    public String prefix() {
        return prefix;
    }
}
