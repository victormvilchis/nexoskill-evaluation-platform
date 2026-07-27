package com.nexoskill.evaluation.globalcontent.infrastructure.persistence;

import com.nexoskill.evaluation.globalcontent.domain.model.EditorialStatus;
import com.nexoskill.evaluation.globalcontent.domain.model.GlobalContentType;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "GLOBAL_CONTENT_PROMOTION")
public class GlobalContentPromotionJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PROMOTION_ID")
    private Long id;

    @Column(name = "PUBLIC_ID", nullable = false, unique = true, length = 36)
    private String publicId;

    @Column(name = "SOURCE_ORGANIZATION_ID", nullable = false)
    private Long sourceOrganizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "SOURCE_CONTENT_TYPE", nullable = false, length = 30)
    private GlobalContentType contentType;

    @Column(name = "SOURCE_CONTENT_ID", nullable = false)
    private Long sourceContentId;

    @Column(name = "SOURCE_CONTENT_PUBLIC_ID", nullable = false, length = 36)
    private String sourceContentPublicId;

    @Column(name = "SOURCE_VERSION", nullable = false)
    private long sourceVersion;

    @Column(name = "SOURCE_AUTHOR_ID")
    private Long sourceAuthorId;

    @Column(name = "GLOBAL_CONTENT_ID", nullable = false)
    private Long globalContentId;

    @Column(name = "GLOBAL_CONTENT_PUBLIC_ID", nullable = false, length = 36)
    private String globalContentPublicId;

    @Column(name = "GLOBAL_VERSION", nullable = false)
    private long globalVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "PROMOTION_STATUS", nullable = false, length = 30)
    private EditorialStatus status;

    @Column(name = "PROMOTION_NOTES", length = 2000)
    private String notes;

    @Column(name = "PROMOTED_BY", nullable = false)
    private Long promotedBy;

    @Column(name = "PROMOTED_AT", nullable = false)
    private Instant promotedAt;

    @Column(name = "REVIEWED_BY")
    private Long reviewedBy;

    @Column(name = "REVIEWED_AT")
    private Instant reviewedAt;

    @Column(name = "PUBLISHED_BY")
    private Long publishedBy;

    @Column(name = "PUBLISHED_AT")
    private Instant publishedAt;

    @Column(name = "REJECTION_REASON", length = 1000)
    private String rejectionReason;

    @Column(name = "FUNCTIONAL_HASH", length = 64)
    private String functionalHash;

    @Version
    @Column(name = "VERSION_NO", nullable = false)
    private long entityVersion;

    protected GlobalContentPromotionJpaEntity() {}

    public static GlobalContentPromotionJpaEntity create(
            String publicId,
            Long sourceOrganizationId,
            GlobalContentType contentType,
            Long sourceContentId,
            String sourceContentPublicId,
            long sourceVersion,
            Long sourceAuthorId,
            Long globalContentId,
            String globalContentPublicId,
            long globalVersion,
            String notes,
            String functionalHash,
            Long actor,
            Instant now) {
        GlobalContentPromotionJpaEntity entity = new GlobalContentPromotionJpaEntity();
        entity.publicId = publicId;
        entity.sourceOrganizationId = sourceOrganizationId;
        entity.contentType = contentType;
        entity.sourceContentId = sourceContentId;
        entity.sourceContentPublicId = sourceContentPublicId;
        entity.sourceVersion = sourceVersion;
        entity.sourceAuthorId = sourceAuthorId;
        entity.globalContentId = globalContentId;
        entity.globalContentPublicId = globalContentPublicId;
        entity.globalVersion = globalVersion;
        entity.status = EditorialStatus.DRAFT;
        entity.notes = notes;
        entity.functionalHash = functionalHash;
        entity.promotedBy = actor;
        entity.promotedAt = now;
        return entity;
    }

    public void submitForReview(Long actor, Instant now) {
        requireStatus(EditorialStatus.DRAFT, EditorialStatus.REJECTED);
        status = EditorialStatus.UNDER_REVIEW;
        reviewedBy = actor;
        reviewedAt = now;
        rejectionReason = null;
    }

    public void publish(Long actor, Instant now) {
        requireStatus(EditorialStatus.UNDER_REVIEW, EditorialStatus.DRAFT);
        status = EditorialStatus.PUBLISHED;
        publishedBy = actor;
        publishedAt = now;
        rejectionReason = null;
    }

    public void reject(Long actor, String reason, Instant now) {
        requireStatus(EditorialStatus.UNDER_REVIEW, EditorialStatus.DRAFT);
        status = EditorialStatus.REJECTED;
        reviewedBy = actor;
        reviewedAt = now;
        rejectionReason = reason;
    }

    public void archive(Long actor, Instant now) {
        if (status == EditorialStatus.ARCHIVED) return;
        status = EditorialStatus.ARCHIVED;
        reviewedBy = actor;
        reviewedAt = now;
    }

    private void requireStatus(EditorialStatus... allowed) {
        for (EditorialStatus candidate : allowed) {
            if (status == candidate) return;
        }
        throw new IllegalStateException("GLOBAL_CONTENT_EDITORIAL_TRANSITION_INVALID");
    }

    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public Long getSourceOrganizationId() { return sourceOrganizationId; }
    public GlobalContentType getContentType() { return contentType; }
    public Long getSourceContentId() { return sourceContentId; }
    public String getSourceContentPublicId() { return sourceContentPublicId; }
    public long getSourceVersion() { return sourceVersion; }
    public Long getSourceAuthorId() { return sourceAuthorId; }
    public Long getGlobalContentId() { return globalContentId; }
    public String getGlobalContentPublicId() { return globalContentPublicId; }
    public long getGlobalVersion() { return globalVersion; }
    public EditorialStatus getStatus() { return status; }
    public String getNotes() { return notes; }
    public Long getPromotedBy() { return promotedBy; }
    public Instant getPromotedAt() { return promotedAt; }
    public Long getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public Long getPublishedBy() { return publishedBy; }
    public Instant getPublishedAt() { return publishedAt; }
    public String getRejectionReason() { return rejectionReason; }
    public String getFunctionalHash() { return functionalHash; }
}
