package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import com.nexoskill.evaluation.questionbank.domain.model.CatalogStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataQuestionCategoryRepository extends JpaRepository<QuestionCategoryJpaEntity, Long> {
	List<QuestionCategoryJpaEntity> findAllByStatusOrderByNameAsc(CatalogStatus status);

	Optional<QuestionCategoryJpaEntity> findByPublicId(String publicId);

	List<QuestionCategoryJpaEntity> findAllByPublicIdIn(Collection<String> ids);

	@Query("""
			select c from QuestionCategoryJpaEntity c
			where (:status is null or c.status = :status)
			  and (c.contentScope = com.nexoskill.evaluation.organizations.domain.model.ContentScope.GLOBAL
			       or (:organizationId is not null and c.ownerOrganizationId = :organizationId))
			order by c.name asc
			""")
	List<QuestionCategoryJpaEntity> findVisible(@Param("organizationId") Long organizationId,
			@Param("status") CatalogStatus status);

	@Query("""
			select c from QuestionCategoryJpaEntity c
			where c.contentScope = :scope
			  and ((:organizationId is null and c.ownerOrganizationId is null)
			       or c.ownerOrganizationId = :organizationId)
			""")
	List<QuestionCategoryJpaEntity> findWithinScope(@Param("scope") ContentScope scope,
			@Param("organizationId") Long organizationId);
}
