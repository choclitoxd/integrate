package com.integrate.common.persistence;

/**
 * Dominios de V1 usados como {@code columnDefinition}. PostgreSQL reporta estas columnas como
 * {@code Types.DISTINCT} con el nombre calificado entre comillas, y {@code ddl-auto: validate}
 * solo las acepta si la entidad declara el mismo nombre de tipo (ADR-0005 §11).
 */
public final class PgDomains {

    public static final String EMAIL = "\"common\".\"email\"";
    public static final String DOC_TYPE = "\"common\".\"doc_type\"";
    public static final String DOC_NUMBER = "\"common\".\"doc_number\"";
    public static final String PHONE_E164 = "\"common\".\"phone_e164\"";
    public static final String SHA256_HEX = "\"common\".\"sha256_hex\"";
    public static final String COP_AMOUNT = "\"common\".\"cop_amount\"";
    public static final String CIPHERTEXT = "\"common\".\"ciphertext\"";
    public static final String S3_KEY = "\"common\".\"s3_key\"";

    private PgDomains() {
    }
}
