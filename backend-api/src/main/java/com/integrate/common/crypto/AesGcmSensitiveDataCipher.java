package com.integrate.common.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM con JCA (ADR-0007 §4): IV aleatorio de 12 bytes por valor y tag de 128 bits. Las
 * llaves se validan al arrancar: si falta la vigente o alguna no mide 32 bytes, la aplicación no
 * inicia (fail-fast, ADR-0002 §4).
 */
@Component
public class AesGcmSensitiveDataCipher implements SensitiveDataCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final Map<Short, SecretKey> keys;
    private final short currentVersion;
    private final SecureRandom random = new SecureRandom();

    public AesGcmSensitiveDataCipher(SensitiveDataProperties properties) {
        Map<Short, SecretKey> parsed = new HashMap<>();
        properties.keys().forEach((version, encoded) -> parsed.put(version, parseKey(version, encoded)));
        if (properties.currentVersion() <= 0 || !parsed.containsKey(properties.currentVersion())) {
            throw new SensitiveDataException("integrate.crypto.sensitive: the key for the current version "
                    + properties.currentVersion() + " is missing (SENSITIVE_DATA_KEY_V" + properties.currentVersion() + ")");
        }
        this.keys = Map.copyOf(parsed);
        this.currentVersion = properties.currentVersion();
    }

    @Override
    public short currentKeyVersion() {
        return currentVersion;
    }

    @Override
    public byte[] encrypt(String plaintext, String aad, short keyVersion) {
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key(keyVersion), new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(IV_BYTES + sealed.length).put(iv).put(sealed).array();
        } catch (GeneralSecurityException e) {
            throw new SensitiveDataException("Encryption failed", e);
        }
    }

    @Override
    public String decrypt(byte[] ciphertext, String aad, short keyVersion) {
        if (ciphertext == null || ciphertext.length < IV_BYTES + TAG_BITS / 8) {
            throw new SensitiveDataException("Ciphertext is too short");
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key(keyVersion), new GCMParameterSpec(TAG_BITS, ciphertext, 0, IV_BYTES));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            byte[] plain = cipher.doFinal(ciphertext, IV_BYTES, ciphertext.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            // AEADBadTagException incluida: dato alterado, aad distinto o llave equivocada.
            throw new SensitiveDataException("Decryption failed", e);
        }
    }

    private SecretKey key(short version) {
        SecretKey key = keys.get(version);
        if (key == null) {
            throw new SensitiveDataException("Unknown sensitive data key version " + version);
        }
        return key;
    }

    private static SecretKey parseKey(short version, String encoded) {
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(encoded == null ? "" : encoded.strip());
        } catch (IllegalArgumentException e) {
            throw new SensitiveDataException("integrate.crypto.sensitive.keys." + version + " is not valid Base64");
        }
        if (raw.length != KEY_BYTES) {
            throw new SensitiveDataException("integrate.crypto.sensitive.keys." + version + " must decode to "
                    + KEY_BYTES + " bytes");
        }
        return new SecretKeySpec(raw, "AES");
    }
}
