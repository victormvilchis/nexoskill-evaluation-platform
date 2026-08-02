package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import com.nexoskill.evaluation.questionbank.domain.model.QuestionTechnologyStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataQuestionTechnologyRepository
        extends JpaRepository<QuestionTechnologyJpaEntity, Long> {
    Optional<QuestionTechnologyJpaEntity> findByPublicId(String publicId);
    Optional<QuestionTechnologyJpaEntity> findByCodeIgnoreCase(String code);
    List<QuestionTechnologyJpaEntity> findByStatusOrderByDisplayOrderAscNameAsc(QuestionTechnologyStatus status);
    List<QuestionTechnologyJpaEntity> findAllByOrderByDisplayOrderAscNameAsc();
    List<QuestionTechnologyJpaEntity> findAllByContentScopeOrderByDisplayOrderAscNameAsc(
            com.nexoskill.evaluation.organizations.domain.model.ContentScope contentScope);
    List<QuestionTechnologyJpaEntity> findAllByContentScopeAndOwnerOrganizationIdOrderByDisplayOrderAscNameAsc(
            com.nexoskill.evaluation.organizations.domain.model.ContentScope contentScope, Long ownerOrganizationId);

    @Query("""
            select t from QuestionTechnologyJpaEntity t
            where (:status is null or t.status = :status)
              and (t.contentScope = com.nexoskill.evaluation.organizations.domain.model.ContentScope.GLOBAL
                   or t.ownerOrganizationId = :organizationId)
            order by t.displayOrder asc, t.name asc
            """)
    List<QuestionTechnologyJpaEntity> findVisible(@Param("organizationId") Long organizationId,
            @Param("status") QuestionTechnologyStatus status);
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByNameIgnoreCase(String name);
}
