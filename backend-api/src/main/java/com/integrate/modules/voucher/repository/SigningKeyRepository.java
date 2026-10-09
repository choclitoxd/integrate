package com.integrate.modules.voucher.repository;

import com.integrate.modules.voucher.entity.SigningKey;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SigningKeyRepository extends JpaRepository<SigningKey, Short> {

    Optional<SigningKey> findByKid(String kid);

    /** A lo sumo una: índice parcial {@code uq_signing_keys_one_active}. */
    Optional<SigningKey> findByStatus(String status);
}
