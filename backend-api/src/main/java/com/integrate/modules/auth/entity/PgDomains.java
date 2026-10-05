package com.integrate.modules.auth.entity;

/**
 * Dominios de V1 usados como {@code columnDefinition}. PostgreSQL reporta estas columnas como
 * {@code Types.DISTINCT} con el nombre calificado entre comillas, y {@code ddl-auto: validate}
 * solo las acepta si la entidad declara el mismo nombre de tipo (ADR-0005 §11).
 */
final class PgDomains {

    static final String EMAIL = "\"common\".\"email\"";
    static final String DOC_TYPE = "\"common\".\"doc_type\"";
    static final String DOC_NUMBER = "\"common\".\"doc_number\"";
    static final String PHONE_E164 = "\"common\".\"phone_e164\"";
    static final String SHA256_HEX = "\"common\".\"sha256_hex\"";

    private PgDomains() {
    }
}
