package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.nexoskill.evaluation.questionbank.domain.model.QuestionStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class QuestionJpaEntityTest {

    @Test
    void clearsPublishedVersionWhenPublishedQuestionIsArchived() {
        QuestionJpaEntity question = QuestionJpaEntity.create(
                "7a6ad962-20a8-4303-b98e-770997f48db8",
                mock(QuestionTypeJpaEntity.class),
                mock(QuestionDifficultyJpaEntity.class),
                mock(QuestionCategoryJpaEntity.class),
                1L,
                Instant.parse("2026-07-24T12:00:00Z")
        );
        QuestionVersionJpaEntity version = mock(QuestionVersionJpaEntity.class);
        question.registerCurrentVersion(version);

        question.transitionTo(QuestionStatus.UNDER_REVIEW, 1L, Instant.now());
        question.transitionTo(QuestionStatus.APPROVED, 1L, Instant.now());
        question.transitionTo(QuestionStatus.PUBLISHED, 1L, Instant.now());
        assertThat(question.getPublishedVersion()).isSameAs(version);

        question.transitionTo(QuestionStatus.ARCHIVED, 1L, Instant.now());

        assertThat(question.getPublishedVersion()).isNull();
    }
}
