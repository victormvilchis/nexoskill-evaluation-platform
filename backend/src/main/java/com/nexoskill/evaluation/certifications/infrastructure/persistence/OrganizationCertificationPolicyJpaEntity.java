package com.nexoskill.evaluation.certifications.infrastructure.persistence;

import com.nexoskill.evaluation.certifications.domain.CertificationType;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ORGANIZATION_CERTIFICATION_POLICY",
       uniqueConstraints = @UniqueConstraint(name = "UK_ORG_CERT_POLICY_TYPE",
               columnNames = {"ORGANIZATION_ID", "CERTIFICATION_TYPE"}))
public class OrganizationCertificationPolicyJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ORGANIZATION_CERT_POLICY_ID") private Long id;
    @Column(name = "PUBLIC_ID", nullable = false, length = 36, unique = true) private String publicId;
    @Column(name = "ORGANIZATION_ID", nullable = false) private Long organizationId;
    @Enumerated(EnumType.STRING)
    @Column(name = "CERTIFICATION_TYPE", nullable = false, length = 40) private CertificationType certificationType;
    @Column(name = "APPLIES_BY_DEFAULT", nullable = false) private boolean appliesByDefault;
    @Column(name = "DEADLINE_MONTHS") private Integer deadlineMonths;
    @Column(name = "DEADLINE_DAYS") private Integer deadlineDays;
    @Column(name = "MAX_ATTEMPTS") private Integer maxAttempts;
    @Column(name = "STATUS", nullable = false, length = 20) private String status;
    @Column(name = "CREATED_BY") private Long createdBy;
    @Column(name = "UPDATED_BY") private Long updatedBy;
    @Column(name = "CREATED_AT", nullable = false) private Instant createdAt;
    @Column(name = "UPDATED_AT", nullable = false) private Instant updatedAt;
    @Version @Column(name = "VERSION_NO", nullable = false) private Long version;
    protected OrganizationCertificationPolicyJpaEntity() {}
    public static OrganizationCertificationPolicyJpaEntity create(Long organizationId, CertificationType type,
            Integer months, Integer days, Long actorId, Instant now) {
        OrganizationCertificationPolicyJpaEntity entity = new OrganizationCertificationPolicyJpaEntity();
        entity.publicId = java.util.UUID.randomUUID().toString();
        entity.organizationId = organizationId;
        entity.certificationType = type;
        entity.appliesByDefault = false;
        entity.deadlineMonths = months;
        entity.deadlineDays = days;
        entity.status = "ACTIVE";
        entity.createdBy = actorId;
        entity.updatedBy = actorId;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }
    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public CertificationType getCertificationType() { return certificationType; }
    public boolean isAppliesByDefault() { return appliesByDefault; }
    public Integer getDeadlineMonths() { return deadlineMonths; }
    public Integer getDeadlineDays() { return deadlineDays; }
    public Integer getMaxAttempts() { return maxAttempts; }
    public String getStatus() { return status; }
}
