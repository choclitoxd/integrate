package com.integrate.common.crypto;

/**
 * Cifrado de datos sensibles en la aplicación (AES-256-GCM, ADR-0007 §4). La llave nunca llega a
 * la base de datos: se guarda el resultado en columnas {@code common.ciphertext} junto con la
 * versión de llave con que se cifró.
 *
 * <p>El {@code aad} ata el ciphertext a su ubicación (tabla, columna y fila): un valor copiado a otra
 * fila o columna no descifra.
 */
public interface SensitiveDataCipher {

    /** Versión de llave con que se cifran los valores nuevos. Todas las columnas de una fila usan la misma. */
    short currentKeyVersion();

    /** @return IV de 12 bytes ‖ texto cifrado ‖ tag de 16 bytes (disposición de {@code common.ciphertext}) */
    byte[] encrypt(String plaintext, String aad, short keyVersion);

    /** @throws SensitiveDataException si la llave no existe, el {@code aad} no coincide o el dato fue alterado */
    String decrypt(byte[] ciphertext, String aad, short keyVersion);
}
