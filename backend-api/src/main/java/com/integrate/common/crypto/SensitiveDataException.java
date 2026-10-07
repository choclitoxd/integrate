package com.integrate.common.crypto;

/** Fallo de cifrado o descifrado. Nunca incluye el dato en claro ni la llave en el mensaje. */
public class SensitiveDataException extends IllegalStateException {

    public SensitiveDataException(String message) {
        super(message);
    }

    public SensitiveDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
