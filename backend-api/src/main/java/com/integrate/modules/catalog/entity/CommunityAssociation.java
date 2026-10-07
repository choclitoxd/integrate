package com.integrate.modules.catalog.entity;

import com.integrate.common.persistence.PgDomains;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Asociación comunitaria titular del NIT y el RNT sombrilla ({@code catalog.community_associations}). Recibe el 85%. */
@Entity
@Table(name = "community_associations", schema = "catalog")
@DynamicUpdate
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityAssociation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 15)
    private String nit;

    @Column(name = "rnt_number", nullable = false, length = 20)
    private String rntNumber;

    @Column(name = "legal_name", nullable = false, length = 200)
    private String legalName;

    @Enumerated(EnumType.STRING)
    @Column(name = "association_type", nullable = false, length = 30)
    private AssociationType associationType;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "municipality_code", nullable = false, length = 5)
    private String municipalityCode;

    @Column(length = 250)
    private String address;

    @Column(name = "contact_email", nullable = false, length = 254, columnDefinition = PgDomains.EMAIL)
    private String contactEmail;

    @Column(name = "contact_phone", nullable = false, length = 16, columnDefinition = PgDomains.PHONE_E164)
    private String contactPhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssociationStatus status;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at", insertable = false, updatable = false)
    private Instant deletedAt;

    public static CommunityAssociation create(String nit, String rntNumber, String legalName, AssociationType type,
                                              String municipalityCode, String address, String contactEmail,
                                              String contactPhone) {
        CommunityAssociation association = new CommunityAssociation();
        association.nit = nit;
        association.rntNumber = rntNumber;
        association.legalName = legalName;
        association.associationType = type;
        association.municipalityCode = municipalityCode;
        association.address = address;
        association.contactEmail = contactEmail;
        association.contactPhone = contactPhone;
        association.status = AssociationStatus.ACTIVE;
        return association;
    }

    public boolean isActive() {
        return status == AssociationStatus.ACTIVE && deletedAt == null;
    }
}
