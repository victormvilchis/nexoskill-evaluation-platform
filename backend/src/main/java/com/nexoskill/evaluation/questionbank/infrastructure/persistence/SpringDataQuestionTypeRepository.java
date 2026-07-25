package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import com.nexoskill.evaluation.questionbank.domain.model.CatalogStatus;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataQuestionTypeRepository extends JpaRepository<QuestionTypeJpaEntity, String> {
	List<QuestionTypeJpaEntity> findAllByStatusOrderByNameAsc(CatalogStatus status);

	Optional<QuestionTypeJpaEntity> findByCodeAndStatus(String code, CatalogStatus status);
}
