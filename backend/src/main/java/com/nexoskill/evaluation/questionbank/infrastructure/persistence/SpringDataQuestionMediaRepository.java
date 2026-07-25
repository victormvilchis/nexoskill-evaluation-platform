package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataQuestionMediaRepository extends JpaRepository<QuestionMediaJpaEntity, Long> {
	Optional<QuestionMediaJpaEntity> findByPublicId(String id);

	List<QuestionMediaJpaEntity> findAllByPublicIdIn(Collection<String> ids);
}
