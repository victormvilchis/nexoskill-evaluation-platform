package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexoskill.evaluation.questionbank.domain.model.QuestionTechnologyStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class QuestionTechnologyJpaEntityTest {
    @Test
    void createsActiveAndPreservesHistoryWhenInactivated() {
        Instant createdAt = Instant.parse("2026-07-27T12:00:00Z");
        QuestionTechnologyJpaEntity technology = QuestionTechnologyJpaEntity.create(
                "00000000-0000-0000-3000-000000000001", "JAVA", "Java",
                "Tecnología Java", 10, 1L, createdAt);

        assertThat(technology.getStatus()).isEqualTo(QuestionTechnologyStatus.ACTIVE);
        assertThat(technology.getCode()).isEqualTo("JAVA");

        technology.changeStatus(QuestionTechnologyStatus.INACTIVE, 2L, createdAt.plusSeconds(60));

        assertThat(technology.getStatus()).isEqualTo(QuestionTechnologyStatus.INACTIVE);
        assertThat(technology.getUpdatedBy()).isEqualTo(2L);
    }
}
