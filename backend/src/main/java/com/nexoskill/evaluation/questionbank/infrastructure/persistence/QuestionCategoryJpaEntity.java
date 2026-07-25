package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import com.nexoskill.evaluation.questionbank.domain.model.CatalogStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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

    @Column(name = "CATEGORY_CODE", nullable = false, unique = true, length = 80)
    private String code;

    @Column(name = "CATEGORY_NAME", nullable = false, unique = true, length = 150)
    private String name;

    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private CatalogStatus status;

    @Column(name = "CREATED_BY")
    private Long createdBy;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @Version
    @Column(name = "VERSION_NO", nullable = false)
    private long version;

    protected QuestionCategoryJpaEntity() {
    }

    public static QuestionCategoryJpaEntity create(
            String publicId,
            String code,
            String name,
            String description,
            Long createdBy,
            Instant createdAt) {
        QuestionCategoryJpaEntity entity = new QuestionCategoryJpaEntity();
        entity.publicId = publicId;
        entity.code = code;
        entity.name = name;
        entity.description = description;
        entity.status = CatalogStatus.ACTIVE;
        entity.createdBy = createdBy;
        entity.createdAt = createdAt;
        return entity;
    }

    public void changeStatus(CatalogStatus targetStatus) {
        this.status = targetStatus;
    }

    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public CatalogStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
