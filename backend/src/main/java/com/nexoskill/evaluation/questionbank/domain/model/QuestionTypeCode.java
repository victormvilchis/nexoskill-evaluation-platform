package com.nexoskill.evaluation.questionbank.domain.model;

public enum QuestionTypeCode {
    SINGLE_CHOICE,
    MULTIPLE_CHOICE,
    TRUE_FALSE,
    MATCHING,
    OPEN_TEXT;

    public boolean usesOptions() {
        return this != OPEN_TEXT;
    }

    public boolean isChoice() {
        return this == SINGLE_CHOICE || this == MULTIPLE_CHOICE || this == TRUE_FALSE;
    }

    public boolean requiresManualReview() {
        return this == OPEN_TEXT;
    }
}
