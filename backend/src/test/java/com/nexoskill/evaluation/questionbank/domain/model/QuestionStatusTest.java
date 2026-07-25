package com.nexoskill.evaluation.questionbank.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QuestionStatusTest {

    @Test
    void onlyPublishedQuestionsCanBeArchivedInTheSimplifiedWorkflow() {
        assertThat(QuestionStatus.PUBLISHED.canTransitionTo(QuestionStatus.ARCHIVED)).isTrue();
        assertThat(QuestionStatus.DRAFT.canTransitionTo(QuestionStatus.UNDER_REVIEW)).isFalse();
        assertThat(QuestionStatus.UNDER_REVIEW.canTransitionTo(QuestionStatus.APPROVED)).isFalse();
        assertThat(QuestionStatus.APPROVED.canTransitionTo(QuestionStatus.PUBLISHED)).isFalse();
        assertThat(QuestionStatus.ARCHIVED.canTransitionTo(QuestionStatus.PUBLISHED)).isFalse();
    }
}
