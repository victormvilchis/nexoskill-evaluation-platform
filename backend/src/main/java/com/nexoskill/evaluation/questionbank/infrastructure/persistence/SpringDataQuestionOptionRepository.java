package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SpringDataQuestionOptionRepository extends JpaRepository<QuestionOptionJpaEntity, Long> {
	@Modifying
	@Query("delete from QuestionOptionJpaEntity o where o.question.id=:id")
	void deleteByQuestionId(@Param("id") Long id);
}
