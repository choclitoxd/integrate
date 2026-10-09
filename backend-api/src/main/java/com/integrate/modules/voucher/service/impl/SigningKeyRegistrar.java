package com.integrate.modules.voucher.service.impl;

import com.integrate.common.crypto.RsaSigningKeys;
import com.integrate.modules.voucher.config.VoucherProperties;
import com.integrate.modules.voucher.entity.SigningKey;
import com.integrate.modules.voucher.repository.SigningKeyRepository;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPrivateKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Registro de la llave pública de vouchers al arrancar (ADR-0010 §2). Si el {@code kid} configurado no existe, se
 * retira la llave {@code ACTIVE} anterior y se activa la nueva; si existe pero no está {@code ACTIVE}, o si su PEM no
 * coincide con la llave privada configurada, el arranque aborta.
 */
@Component
public class SigningKeyRegistrar implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SigningKeyRegistrar.class);

    private final SigningKeyRepository repository;
    private final Clock clock;
    private final RsaSigningKeys.KeyPair keyPair;
    private final String kid;
    private final TransactionTemplate transaction;
    private volatile ActiveSigningKey active;

    public SigningKeyRegistrar(SigningKeyRepository repository, VoucherProperties properties, Clock clock,
                               PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.clock = clock;
        // Llamada interna: el @Transactional de register() no aplicaría sin el proxy.
        this.transaction = new TransactionTemplate(transactionManager);
        this.keyPair = RsaSigningKeys.load(properties.signingKey(), "VOUCHER_SIGNING_KEY");
        this.kid = properties.signingKeyId();
    }

    @Override
    public void run(ApplicationArguments args) {
        active();
    }

    /** Llave con la que se firman los vouchers nuevos; la registra en la primera llamada. */
    public ActiveSigningKey active() {
        ActiveSigningKey current = active;
        if (current == null) {
            synchronized (this) {
                if (active == null) {
                    active = registerWithRetry();
                }
                current = active;
            }
        }
        return current;
    }

    /** Dos instancias que arrancan a la vez compiten por {@code uq_signing_keys_kid}: la perdedora relee. */
    private ActiveSigningKey registerWithRetry() {
        try {
            return transaction.execute(status -> register(keyPair, kid));
        } catch (DataIntegrityViolationException e) {
            return transaction.execute(status -> register(keyPair, kid));
        }
    }

    /**
     * Activa {@code kid} con la llave pública de {@code pair}. Une la transacción de quien llama (las pruebas de
     * rotación la revierten).
     */
    @Transactional
    public ActiveSigningKey register(RsaSigningKeys.KeyPair pair, String keyId) {
        String pem = publicPem(pair);
        Optional<SigningKey> existing = repository.findByKid(keyId);
        SigningKey key;
        if (existing.isPresent()) {
            key = existing.get();
            if (!key.isActive()) {
                throw new IllegalStateException("VOUCHER_SIGNING_KEY_ID " + keyId + " is " + key.getStatus()
                        + "; configure a new key id (ADR-0010 §2)");
            }
            if (!key.getPublicKeyPem().equals(pem)) {
                throw new IllegalStateException("VOUCHER_SIGNING_KEY_ID " + keyId
                        + " is registered with a different public key");
            }
        } else {
            Instant now = clock.instant();
            repository.findByStatus(SigningKey.ACTIVE).ifPresent(previous -> {
                previous.retire(now);
                log.info("voucher.signing-key retired kid={}", previous.getKid());
            });
            repository.flush();
            key = repository.saveAndFlush(SigningKey.activate(keyId, pem, now));
            log.info("voucher.signing-key activated kid={}", keyId);
        }
        return new ActiveSigningKey(key.getId(), keyId, pair.privateKey(), pem);
    }

    /** Llave pública en PEM SPKI ({@code BEGIN PUBLIC KEY}), el formato que importa Web Crypto ({@code spki}). */
    static String publicPem(RsaSigningKeys.KeyPair pair) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(pair.publicKey().getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + body + "\n-----END PUBLIC KEY-----\n";
    }

    /**
     * @param id            {@code voucher.signing_keys.id}
     * @param publicKeyPem  PEM SPKI publicado
     */
    public record ActiveSigningKey(short id, String kid, RSAPrivateKey privateKey, String publicKeyPem) {

        @Override
        public String toString() {
            return "ActiveSigningKey[id=" + id + ", kid=" + kid + "]";
        }
    }
}
