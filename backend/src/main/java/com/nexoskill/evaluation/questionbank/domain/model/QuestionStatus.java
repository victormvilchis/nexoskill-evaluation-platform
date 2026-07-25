package com.nexoskill.evaluation.questionbank.domain.model;

public enum QuestionStatus {
    DRAFT,
    UNDER_REVIEW,
    APPROVED,
    PUBLISHED,
    ARCHIVED;

    public boolean canTransitionTo(QuestionStatus target) {
        return switch (this) {
            case DRAFT -> target == UNDER_REVIEW;
            case UNDER_REVIEW -> target == APPROVED || target == DRAFT;
            case APPROVED -> target == PUBLISHED || target == DRAFT;
            case PUBLISHED -> target == ARCHIVED;
            case ARCHIVED -> false;
        };
    }
}
