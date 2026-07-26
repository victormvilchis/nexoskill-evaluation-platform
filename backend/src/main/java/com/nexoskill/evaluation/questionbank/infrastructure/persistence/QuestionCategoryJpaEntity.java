package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import com.nexoskill.evaluation.questionbank.domain.model.CatalogStatus;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "QUESTION_CATEGORY")
public class QuestionCategoryJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CATEGORY_ID")
    private Long id;

    @Column(name = "PUBLIC_ID", nullable = false, unique = true, length = 36)
    private String publicId;

    @Column(name = "CATEGORY_CODE", nullable = false, length = 80)
    private String code;

    @Column(name = "CATEGORY_NAME", nullable = false, length = 150)
    private String name;

    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private CatalogStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "CONTENT_SCOPE", nullable = false, length = 20)
    private ContentScope contentScope;

    @Column(name = "OWNER_ORGANIZATION_ID")
    private Long ownerOrganizationId;

    @Column(name = "SOURCE_GLOBAL_ID")
    private Long sourceGlobalId;

    @Column(name = "CREATED_BY")
    private Long createdBy;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_BY")
    private Long updatedBy;

    @Column(name = "UPDATED_AT")
    private Instant updatedAt;

    @Version
    @Column(name = "VERSION_NO", nullable = false)
    private long version;

    protected QuestionCategoryJpaEntity() {}

    public static QuestionCategoryJpaEntity create(String publicId, String code, String name, String description,
                                                    ContentScope scope, Long ownerOrganizationId,
                                                    Long actorUserId, Instant now) {
        if (scope == ContentScope.GLOBAL && ownerOrganizationId != null) {
            throw new IllegalArgumentException("Una categoría global no debe tener organización propietaria.");
        }
        if (scope == ContentScope.ORGANIZATION && ownerOrganizationId == null) {
            throw new IllegalArgumentException("Una categoría organizacional requiere organización propietaria.");
        }
        QuestionCategoryJpaEntity entity = new QuestionCategoryJpaEntity();
        entity.publicId = publicId;
        entity.code = code;
        entity.name = name;
        entity.description = description;
        entity.status = CatalogStatus.ACTIVE;
        entity.contentScope = scope;
        entity.ownerOrganizationId = ownerOrganizationId;
        entity.createdBy = actorUserId;
        entity.createdAt = now;
        return entity;
    }

    public void update(String code, String name, String description, Long actorUserId, Instant now) {
        ensureNotDeleted();
        this.code = code;
        this.name = name;
        this.description = description;
        this.updatedBy = actorUserId;
        this.updatedAt = now;
    }

    public void activate(Long actorUserId, Instant now) {
        if (status == CatalogStatus.DELETED) {
            throw new IllegalStateException("Una categoría eliminada requiere un flujo de restauración específico.");
        }
        status = CatalogStatus.ACTIVE;
        updatedBy = actorUserId;
        updatedAt = now;
    }

    public void deactivate(Long actorUserId, Instant now) {
        ensureNotDeleted();
        status = CatalogStatus.INACTIVE;
        updatedBy = actorUserId;
        updatedAt = now;
    }

    public void softDelete(Long actorUserId, Instant now) {
        if (status != CatalogStatus.INACTIVE) {
            throw new IllegalStateException("La categoría debe estar inactiva antes de eliminarse.");
        }
        status = CatalogStatus.DELETED;
        updatedBy = actorUserId;
        updatedAt = now;
    }

    private void ensureNotDeleted() {
        if (status == CatalogStatus.DELETED) {
            throw new IllegalStateException("La categoría está eliminada.");
        }
    }

    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public CatalogStatus getStatus() { return status; }
    public ContentScope getContentScope() { return contentScope; }
    public Long getOwnerOrganizationId() { return ownerOrganizationId; }
    public Long getSourceGlobalId() { return sourceGlobalId; }
    public Long getCreatedBy() { return createdBy; }
    public Long getUpdatedBy() { return updatedBy; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
