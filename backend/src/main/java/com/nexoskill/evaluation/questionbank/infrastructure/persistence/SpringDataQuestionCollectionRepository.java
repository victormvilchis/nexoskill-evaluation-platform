package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SpringDataQuestionCollectionRepository extends JpaRepository<QuestionCollectionJpaEntity, Long> {
	Optional<QuestionCollectionJpaEntity> findByPublicId(String id);

	boolean existsByNormalizedName(String name);

	boolean existsByNormalizedNameAndPublicIdNot(String name, String id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select distinct c from QuestionCollectionJpaEntity c left join fetch c.categories left join fetch c.questions where c.publicId=:id")
	Optional<QuestionCollectionJpaEntity> findForUpdate(@Param("id") String id);

	@Query(value = "SELECT * FROM QUESTION_COLLECTION c WHERE (:query IS NULL OR LOWER(c.COLLECTION_NAME) LIKE '%'||:query||'%') AND (:status IS NULL OR c.STATUS=:status) ORDER BY c.CREATED_AT DESC", countQuery = "SELECT COUNT(*) FROM QUESTION_COLLECTION c WHERE (:query IS NULL OR LOWER(c.COLLECTION_NAME) LIKE '%'||:query||'%') AND (:status IS NULL OR c.STATUS=:status)", nativeQuery = true)
	Page<QuestionCollectionJpaEntity> search(@Param("query") String q, @Param("status") String s, Pageable p);
}
