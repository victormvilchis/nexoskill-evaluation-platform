package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import com.nexoskill.evaluation.questionbank.domain.model.QuestionStatus;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "QUESTION")
public class QuestionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "QUESTION_ID")
    private Long id;

    @Column(name = "PUBLIC_ID", nullable = false, unique = true, length = 36)
    private String publicId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "TYPE_CODE", nullable = false)
    private QuestionTypeJpaEntity type;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "DIFFICULTY_CODE", nullable = false)
    private QuestionDifficultyJpaEntity difficulty;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "CATEGORY_ID", nullable = false)
    private QuestionCategoryJpaEntity category;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 30)
    private QuestionStatus status;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "CURRENT_VERSION_ID")
    private QuestionVersionJpaEntity currentVersion;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "PUBLISHED_VERSION_ID")
    private QuestionVersionJpaEntity publishedVersion;

    @Column(name = "CREATED_BY", nullable = false)
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

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<QuestionVersionJpaEntity> versions = new ArrayList<>();

    protected QuestionJpaEntity() {
    }

    public static QuestionJpaEntity create(
            String publicId,
            QuestionTypeJpaEntity type,
            QuestionDifficultyJpaEntity difficulty,
            QuestionCategoryJpaEntity category,
            Long createdBy,
            Instant createdAt) {
        QuestionJpaEntity entity = new QuestionJpaEntity();
        entity.publicId = publicId;
        entity.type = type;
        entity.difficulty = difficulty;
        entity.category = category;
        entity.status = QuestionStatus.DRAFT;
        entity.createdBy = createdBy;
        entity.createdAt = createdAt;
        return entity;
    }

    public void registerCurrentVersion(QuestionVersionJpaEntity version) {
        if (!versions.contains(version)) versions.add(version);
        currentVersion = version;
    }

    public void registerNewDraftVersion(
            QuestionVersionJpaEntity newVersion,
            Long actorUserId,
            Instant now) {
        registerCurrentVersion(newVersion);
        status = QuestionStatus.DRAFT;
        touch(actorUserId, now);
    }

    public void updateClassification(
            QuestionTypeJpaEntity type,
            QuestionDifficultyJpaEntity difficulty,
            QuestionCategoryJpaEntity category,
            Long actorUserId,
            Instant now) {
        this.type = type;
        this.difficulty = difficulty;
        this.category = category;
        touch(actorUserId, now);
    }

    public void transitionTo(QuestionStatus target, Long actorUserId, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new BusinessException(
                    "QUESTION_TRANSITION_NOT_ALLOWED",
                    "La transición editorial solicitada no está permitida."
            );
        }
        status = target;
        if (target == QuestionStatus.PUBLISHED) {
            publishedVersion = currentVersion;
        } else if (target == QuestionStatus.ARCHIVED
                && publishedVersion == currentVersion) {
            publishedVersion = null;
        }
        touch(actorUserId, now);
    }

    private void touch(Long actorUserId, Instant now) {
        updatedBy = actorUserId;
        updatedAt = now;
    }

    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public QuestionTypeJpaEntity getType() { return type; }
    public QuestionDifficultyJpaEntity getDifficulty() { return difficulty; }
    public QuestionCategoryJpaEntity getCategory() { return category; }
    public QuestionStatus getStatus() { return status; }
    public QuestionVersionJpaEntity getCurrentVersion() { return currentVersion; }
    public QuestionVersionJpaEntity getPublishedVersion() { return publishedVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
