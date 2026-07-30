package com.nexoskill.evaluation.certifications.infrastructure.persistence;

import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "TECHNOLOGICAL_PROFILE_CATALOG")
public class TechnologicalProfileCatalogJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "TECHNOLOGICAL_PROFILE_ID")
    private Long id;

    @Column(name = "PUBLIC_ID", nullable = false, unique = true, length = 36)
    private String publicId;

    @Column(name = "PROFILE_CODE", nullable = false, length = 40)
    private String code;

    @Column(name = "PROFILE_NAME", nullable = false, length = 120)
    private String name;

    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    @Column(name = "STATUS", nullable = false, length = 20)
    private String status;

    @Enumerated(EnumType.STRING)
    @Column(name = "CONTENT_SCOPE", nullable = false, length = 20)
    private ContentScope contentScope;

    @Column(name = "OWNER_ORGANIZATION_ID")
    private Long ownerOrganizationId;

    @Column(name = "DISPLAY_ORDER", nullable = false)
    private int displayOrder;

    @Column(name = "CREATED_BY")
    private Long createdBy;

    @Column(name = "UPDATED_BY")
    private Long updatedBy;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "VERSION_NO", nullable = false)
    private Long version;

    protected TechnologicalProfileCatalogJpaEntity() {}

    public static TechnologicalProfileCatalogJpaEntity create(String publicId, String code, String name,
            String description, int displayOrder, ContentScope contentScope, Long ownerOrganizationId,
            Long actorId, Instant now) {
        TechnologicalProfileCatalogJpaEntity entity = new TechnologicalProfileCatalogJpaEntity();
        entity.publicId = publicId;
        entity.code = code;
        entity.name = name;
        entity.description = description;
        entity.status = "ACTIVE";
        entity.contentScope = contentScope;
        entity.ownerOrganizationId = ownerOrganizationId;
        entity.displayOrder = displayOrder;
        entity.createdBy = actorId;
        entity.updatedBy = actorId;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void update(String name, String description, int displayOrder, Long actorId, Instant now) {
        this.name = name;
        this.description = description;
        this.displayOrder = displayOrder;
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
    public ContentScope getContentScope() { return contentScope; }
    public Long getOwnerOrganizationId() { return ownerOrganizationId; }
    public int getDisplayOrder() { return displayOrder; }
    public Long getCreatedBy() { return createdBy; }
    public Long getUpdatedBy() { return updatedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
