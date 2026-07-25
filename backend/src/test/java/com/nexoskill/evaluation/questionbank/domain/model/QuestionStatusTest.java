package com.nexoskill.evaluation.questionbank.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QuestionStatusTest {

    @Test
    void allowsOnlyConfiguredWorkflowTransitions() {
        assertThat(QuestionStatus.DRAFT.canTransitionTo(QuestionStatus.UNDER_REVIEW)).isTrue();
        assertThat(QuestionStatus.UNDER_REVIEW.canTransitionTo(QuestionStatus.APPROVED)).isTrue();
        assertThat(QuestionStatus.UNDER_REVIEW.canTransitionTo(QuestionStatus.DRAFT)).isTrue();
        assertThat(QuestionStatus.APPROVED.canTransitionTo(QuestionStatus.PUBLISHED)).isTrue();
        assertThat(QuestionStatus.APPROVED.canTransitionTo(QuestionStatus.DRAFT)).isTrue();
        assertThat(QuestionStatus.PUBLISHED.canTransitionTo(QuestionStatus.ARCHIVED)).isTrue();

        assertThat(QuestionStatus.DRAFT.canTransitionTo(QuestionStatus.PUBLISHED)).isFalse();
        assertThat(QuestionStatus.PUBLISHED.canTransitionTo(QuestionStatus.DRAFT)).isFalse();
        assertThat(QuestionStatus.ARCHIVED.canTransitionTo(QuestionStatus.PUBLISHED)).isFalse();
    }
}
