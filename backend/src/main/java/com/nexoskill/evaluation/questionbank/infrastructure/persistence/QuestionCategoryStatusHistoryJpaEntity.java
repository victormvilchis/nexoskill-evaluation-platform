package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import com.nexoskill.evaluation.questionbank.domain.model.CatalogStatus;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "QUESTION_CATEGORY_STATUS_HISTORY")
public class QuestionCategoryStatusHistoryJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CATEGORY_STATUS_HISTORY_ID")
    private Long id;

    @Column(name = "CATEGORY_ID", nullable = false)
    private Long categoryId;

    @Column(name = "ACTOR_USER_ID")
    private Long actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "PREVIOUS_STATUS", length = 20)
    private CatalogStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "NEW_STATUS", nullable = false, length = 20)
    private CatalogStatus newStatus;

    @Column(name = "REASON", length = 500)
    private String reason;

    @Column(name = "OCCURRED_AT", nullable = false)
    private Instant occurredAt;

    protected QuestionCategoryStatusHistoryJpaEntity() {}

    public static QuestionCategoryStatusHistoryJpaEntity create(Long categoryId, Long actorUserId,
            CatalogStatus previousStatus, CatalogStatus newStatus, String reason, Instant occurredAt) {
        QuestionCategoryStatusHistoryJpaEntity entity = new QuestionCategoryStatusHistoryJpaEntity();
        entity.categoryId = categoryId;
        entity.actorUserId = actorUserId;
        entity.previousStatus = previousStatus;
        entity.newStatus = newStatus;
        entity.reason = reason;
        entity.occurredAt = occurredAt;
        return entity;
    }

    public Long getId() { return id; }
    public Long getCategoryId() { return categoryId; }
    public Long getActorUserId() { return actorUserId; }
    public CatalogStatus getPreviousStatus() { return previousStatus; }
    public CatalogStatus getNewStatus() { return newStatus; }
    public String getReason() { return reason; }
    public Instant getOccurredAt() { return occurredAt; }
}
