package com.nexoskill.evaluation.certifications.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "CERTIFICATION_PROFILE_CATALOG")
public class ProfessionalCertificationProfileJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CERTIFICATION_PROFILE_ID") private Long id;
    @Column(name = "PUBLIC_ID", nullable = false, length = 36, unique = true) private String publicId;
    @Column(name = "PROFILE_CODE", nullable = false, length = 120, unique = true) private String code;
    @Column(name = "PROFILE_NAME", nullable = false, length = 200) private String name;
    @Column(name = "DESCRIPTION", length = 500) private String description;
    @Column(name = "STATUS", nullable = false, length = 20) private String status;
    @Column(name = "SUGGESTED_TECH_PROFILE", length = 40) private String suggestedTechnologicalProfile;
    @Column(name = "SORT_ORDER", nullable = false) private int sortOrder;
    @Column(name = "CREATED_BY") private Long createdBy;
    @Column(name = "UPDATED_BY") private Long updatedBy;
    @Column(name = "CREATED_AT", nullable = false) private Instant createdAt;
    @Column(name = "UPDATED_AT", nullable = false) private Instant updatedAt;
    @Version @Column(name = "VERSION_NO", nullable = false) private Long version;

    protected ProfessionalCertificationProfileJpaEntity() {}

    public static ProfessionalCertificationProfileJpaEntity create(String publicId, String code, String name,
            String description, int sortOrder, String suggested, Long actorId, Instant now) {
        ProfessionalCertificationProfileJpaEntity entity = new ProfessionalCertificationProfileJpaEntity();
        entity.publicId = publicId;
        entity.code = code;
        entity.name = name;
        entity.description = description;
        entity.status = "ACTIVE";
        entity.sortOrder = sortOrder;
        entity.suggestedTechnologicalProfile = suggested;
        entity.createdBy = actorId;
        entity.updatedBy = actorId;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void update(String name, String description, int sortOrder,
            String suggested, Long actorId, Instant now) {
        this.name = name;
        this.description = description;
        this.sortOrder = sortOrder;
        this.suggestedTechnologicalProfile = suggested;
        this.updatedBy = actorId;
        this.updatedAt = now;
    }

    public void changeStatus(String status, Long actorId, Instant now) {
        this.status = status;
        this.updatedBy = actorId;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
    public String getSuggestedTechnologicalProfile() { return suggestedTechnologicalProfile; }
    public int getSortOrder() { return sortOrder; }
    public Long getCreatedBy() { return createdBy; }
    public Long getUpdatedBy() { return updatedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
