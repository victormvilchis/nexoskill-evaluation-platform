package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataQuestionVersionRepository
        extends JpaRepository<QuestionVersionJpaEntity, Long> {
}
