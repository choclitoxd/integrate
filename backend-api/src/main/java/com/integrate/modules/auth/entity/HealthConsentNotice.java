package com.integrate.modules.auth.entity;

import com.integrate.common.persistence.PgDomains;
import com.integrate.modules.auth.service.model.HealthNotice;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "health_consent_notices", schema = "auth")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HealthConsentNotice {

    @Id
    @Column(name = "notice_version", length = 40)
    private String noticeVersion;

    @Column(name = "policy_id", nullable = false)
    private Short policyId;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "content_sha256", nullable = false, columnDefinition = PgDomains.SHA256_HEX)
    private String contentSha256;

    @Column(name = "approved_for_host", nullable = false)
    private boolean approvedForHost;

    public HealthNotice toNotice() {
        return new HealthNotice(noticeVersion, body, contentSha256);
    }
}
