package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionCategoryStatusHistoryRepository
        extends JpaRepository<QuestionCategoryStatusHistoryJpaEntity, Long> {
    List<QuestionCategoryStatusHistoryJpaEntity> findAllByCategoryIdOrderByOccurredAtDesc(Long categoryId);
}
