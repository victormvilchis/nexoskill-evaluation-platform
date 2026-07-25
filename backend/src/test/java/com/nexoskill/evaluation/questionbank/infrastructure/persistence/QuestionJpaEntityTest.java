package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.nexoskill.evaluation.questionbank.domain.model.QuestionStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class QuestionJpaEntityTest {

    @Test
    void publishesImmediatelyAndClearsPublishedReferenceWhenArchived() {
        QuestionJpaEntity question = QuestionJpaEntity.create(
                "7a6ad962-20a8-4303-b98e-770997f48db8",
                mock(QuestionTypeJpaEntity.class),
                mock(QuestionDifficultyJpaEntity.class),
                mock(QuestionCategoryJpaEntity.class),
                1L,
                Instant.parse("2026-07-24T12:00:00Z")
        );
        QuestionVersionJpaEntity version = mock(QuestionVersionJpaEntity.class);

        question.registerPublishedVersion(
                version,
                1L,
                Instant.parse("2026-07-24T12:01:00Z")
        );

        assertThat(question.getStatus()).isEqualTo(QuestionStatus.PUBLISHED);
        assertThat(question.getCurrentVersion()).isSameAs(version);
        assertThat(question.getPublishedVersion()).isSameAs(version);

        question.archive(1L, Instant.parse("2026-07-24T12:02:00Z"));

        assertThat(question.getStatus()).isEqualTo(QuestionStatus.ARCHIVED);
        assertThat(question.getCurrentVersion()).isSameAs(version);
        assertThat(question.getPublishedVersion()).isNull();
    }
}
