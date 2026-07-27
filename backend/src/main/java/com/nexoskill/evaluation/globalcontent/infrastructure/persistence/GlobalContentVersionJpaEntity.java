package com.nexoskill.evaluation.globalcontent.infrastructure.persistence;

import com.nexoskill.evaluation.globalcontent.domain.model.EditorialStatus;
import com.nexoskill.evaluation.globalcontent.domain.model.GlobalContentType;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "GLOBAL_CONTENT_VERSION")
public class GlobalContentVersionJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "GLOBAL_CONTENT_VERSION_ID")
    private Long id;

    @Column(name = "PUBLIC_ID", nullable = false, unique = true, length = 36)
    private String publicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "CONTENT_TYPE", nullable = false, length = 30)
    private GlobalContentType contentType;

    @Column(name = "CONTENT_ID", nullable = false)
    private Long contentId;

    @Column(name = "CONTENT_PUBLIC_ID", nullable = false, length = 36)
    private String contentPublicId;

    @Column(name = "VERSION_NUMBER", nullable = false)
    private long versionNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "EDITORIAL_STATUS", nullable = false, length = 30)
    private EditorialStatus status;

    @Column(name = "PROMOTION_ID")
    private Long promotionId;

    @Column(name = "FUNCTIONAL_HASH", length = 64)
    private String functionalHash;

    @Column(name = "CHANGE_NOTES", length = 2000)
    private String changeNotes;

    @Column(name = "CREATED_BY", nullable = false)
    private Long createdBy;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @Column(name = "PUBLISHED_BY")
    private Long publishedBy;

    @Column(name = "PUBLISHED_AT")
    private Instant publishedAt;

    protected GlobalContentVersionJpaEntity() {}

    public static GlobalContentVersionJpaEntity create(
            String publicId,
            GlobalContentType contentType,
            Long contentId,
            String contentPublicId,
            long versionNumber,
            Long promotionId,
            String functionalHash,
            String changeNotes,
            Long actor,
            Instant now) {
        GlobalContentVersionJpaEntity entity = new GlobalContentVersionJpaEntity();
        entity.publicId = publicId;
        entity.contentType = contentType;
        entity.contentId = contentId;
        entity.contentPublicId = contentPublicId;
        entity.versionNumber = versionNumber;
        entity.status = EditorialStatus.DRAFT;
        entity.promotionId = promotionId;
        entity.functionalHash = functionalHash;
        entity.changeNotes = changeNotes;
        entity.createdBy = actor;
        entity.createdAt = now;
        return entity;
    }

    public void markUnderReview() {
        if (status != EditorialStatus.DRAFT && status != EditorialStatus.REJECTED) {
            throw new IllegalStateException("GLOBAL_CONTENT_VERSION_TRANSITION_INVALID");
        }
        status = EditorialStatus.UNDER_REVIEW;
    }

    public void publish(Long actor, Instant now) {
        if (status != EditorialStatus.UNDER_REVIEW && status != EditorialStatus.DRAFT) {
            throw new IllegalStateException("GLOBAL_CONTENT_VERSION_TRANSITION_INVALID");
        }
        status = EditorialStatus.PUBLISHED;
        publishedBy = actor;
        publishedAt = now;
    }

    public void reject() {
        if (status != EditorialStatus.UNDER_REVIEW && status != EditorialStatus.DRAFT) {
            throw new IllegalStateException("GLOBAL_CONTENT_VERSION_TRANSITION_INVALID");
        }
        status = EditorialStatus.REJECTED;
    }

    public void archive() { status = EditorialStatus.ARCHIVED; }

    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public GlobalContentType getContentType() { return contentType; }
    public Long getContentId() { return contentId; }
    public String getContentPublicId() { return contentPublicId; }
    public long getVersionNumber() { return versionNumber; }
    public EditorialStatus getStatus() { return status; }
    public Long getPromotionId() { return promotionId; }
    public String getFunctionalHash() { return functionalHash; }
    public String getChangeNotes() { return changeNotes; }
    public Long getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getPublishedBy() { return publishedBy; }
    public Instant getPublishedAt() { return publishedAt; }
}
