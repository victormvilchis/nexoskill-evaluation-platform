package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import com.nexoskill.evaluation.questionbank.domain.model.CatalogStatus;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SpringDataQuestionCategoryRepository extends JpaRepository<QuestionCategoryJpaEntity, Long> {
	List<QuestionCategoryJpaEntity> findAllByStatusOrderByNameAsc(CatalogStatus s);

	List<QuestionCategoryJpaEntity> findAllByOrderByNameAsc();

	Optional<QuestionCategoryJpaEntity> findByPublicId(String id);

	List<QuestionCategoryJpaEntity> findAllByPublicIdIn(Collection<String> ids);

	boolean existsByCodeIgnoreCase(String code);

	@Query("select count(c)>0 from QuestionCategoryJpaEntity c where lower(c.name)=:name")
	boolean existsByNormalizedName(@Param("name") String name);
}
