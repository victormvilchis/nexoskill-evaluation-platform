package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import com.nexoskill.evaluation.questionbank.domain.model.CatalogStatus;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataQuestionDifficultyRepository extends JpaRepository<QuestionDifficultyJpaEntity, String> {
    List<QuestionDifficultyJpaEntity> findAllByStatusOrderBySortOrderAsc(CatalogStatus status);
    List<QuestionDifficultyJpaEntity> findAllByOrderBySortOrderAsc();
    Optional<QuestionDifficultyJpaEntity> findByCodeAndStatus(String code, CatalogStatus status);
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByNameIgnoreCase(String name);
}
