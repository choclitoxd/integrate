package com.integrate.modules.catalog.entity;

import com.integrate.common.persistence.PgDomains;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Perfil de anfitrión ({@code catalog.host_profiles}). Los datos de víctima ({@code *_enc}) llegan ya
 * cifrados con AES-256-GCM (ADR-0007 §4) y <b>no tienen getter</b>: ningún DTO puede filtrarlos por
 * accidente (R-05). El identificador se asigna en la aplicación porque forma parte del AAD del cifrado.
 */
@Entity
@Table(name = "host_profiles", schema = "catalog")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HostProfile {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "association_id", nullable = false, updatable = false)
    private UUID associationId;

    @Getter(AccessLevel.NONE)
    @Column(name = "victim_condition_enc", columnDefinition = PgDomains.CIPHERTEXT)
    private byte[] victimConditionEnc;

    @Getter(AccessLevel.NONE)
    @Column(name = "ruv_registration_number_enc", columnDefinition = PgDomains.CIPHERTEXT)
    private byte[] ruvRegistrationNumberEnc;

    @Getter(AccessLevel.NONE)
    @Column(name = "victimization_type_enc", columnDefinition = PgDomains.CIPHERTEXT)
    private byte[] victimizationTypeEnc;

    @Getter(AccessLevel.NONE)
    @Column(name = "support_doc_s3_key", length = 1024, columnDefinition = PgDomains.S3_KEY)
    private String supportDocS3Key;

    @Column(name = "sensitive_key_version")
    private Short sensitiveKeyVersion;

    @Column(name = "is_verified", nullable = false)
    private boolean verified;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at", insertable = false, updatable = false)
    private Instant deletedAt;

    /** Alta mínima por el administrador, sin datos de víctima (ADR-0006 §3). */
    public static HostProfile create(UUID userId, UUID associationId) {
        HostProfile profile = new HostProfile();
        profile.id = UUID.randomUUID();
        profile.userId = userId;
        profile.associationId = associationId;
        return profile;
    }

    /** AAD de una columna cifrada de esta fila: el ciphertext no descifra en otra fila ni columna. */
    public static String aad(String column, UUID hostProfileId) {
        return "catalog.host_profiles." + column + ":" + hostProfileId;
    }

    /**
     * Alta por el registro asistido (ADR-0007 §3) con los datos de víctima ya cifrados con
     * {@link #aad(String, UUID)} y {@code keyVersion}.
     */
    public static HostProfile enroll(UUID id, UUID userId, UUID associationId, VictimCiphertexts victimData) {
        HostProfile profile = new HostProfile();
        profile.id = id;
        profile.userId = userId;
        profile.associationId = associationId;
        profile.victimConditionEnc = victimData.victimCondition();
        profile.ruvRegistrationNumberEnc = victimData.ruvRegistrationNumber();
        profile.victimizationTypeEnc = victimData.victimizationType();
        profile.supportDocS3Key = victimData.supportDocS3Key();
        profile.sensitiveKeyVersion = victimData.keyVersion();
        return profile;
    }

    /** Columnas sensibles ya cifradas; {@code supportDocS3Key} no se cifra (no contiene datos personales). */
    public record VictimCiphertexts(byte[] victimCondition, byte[] ruvRegistrationNumber, byte[] victimizationType,
                                    String supportDocS3Key, short keyVersion) {

        @Override
        public String toString() {
            return "VictimCiphertexts[keyVersion=" + keyVersion + "]";
        }
    }
}
