package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataQuestionVersionRepository
        extends JpaRepository<QuestionVersionJpaEntity, Long> {

    List<QuestionVersionJpaEntity> findByQuestion_IdOrderByVersionNumberDesc(Long questionId);
}
