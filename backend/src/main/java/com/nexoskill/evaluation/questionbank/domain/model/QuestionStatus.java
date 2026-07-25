package com.nexoskill.evaluation.questionbank.domain.model;

/**
 * Editorial states retained for compatibility with historical records.
 * New questions are published immediately; the only interactive transition
 * available in the simplified workflow is PUBLISHED -> ARCHIVED.
 */
public enum QuestionStatus {
    DRAFT,
    UNDER_REVIEW,
    APPROVED,
    PUBLISHED,
    ARCHIVED;

    public boolean canTransitionTo(QuestionStatus target) {
        return this == PUBLISHED && target == ARCHIVED;
    }
}
