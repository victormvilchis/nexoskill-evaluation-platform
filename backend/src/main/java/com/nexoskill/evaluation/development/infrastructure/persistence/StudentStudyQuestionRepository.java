package com.nexoskill.evaluation.development.infrastructure.persistence;

import com.nexoskill.evaluation.questionbank.infrastructure.persistence.QuestionJpaEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface StudentStudyQuestionRepository extends Repository<QuestionJpaEntity, Long> {
    Optional<QuestionJpaEntity> findById(Long id);
    List<QuestionJpaEntity> findAllByIdIn(Collection<Long> ids);
    @Query(value = """
            SELECT q.*
              FROM QUESTION q
             WHERE q.STATUS = 'ACTIVE'
               AND q.TYPE_CODE IN ('SINGLE_CHOICE','MULTIPLE_CHOICE','TRUE_FALSE')
               AND (
                    (q.CONTENT_SCOPE = 'ORGANIZATION' AND q.OWNER_ORGANIZATION_ID = :organizationId)
                    OR (
                        q.CONTENT_SCOPE = 'GLOBAL'
                        AND (
                            NVL(q.AVAILABILITY_MODE, 'NONE') = 'GLOBAL'
                            OR (
                                NVL(q.AVAILABILITY_MODE, 'NONE') = 'SELECTED_ORGANIZATIONS'
                                AND EXISTS (
                                SELECT 1
                                  FROM QUESTION_ORGANIZATION_AVAILABILITY availability
                                 WHERE availability.QUESTION_ID = q.QUESTION_ID
                                   AND availability.ORGANIZATION_ID = :organizationId
                                   AND availability.STATUS = 'ACTIVE'
                                )
                            )
                        )
                        AND (
                            NOT EXISTS (
                                SELECT 1 FROM GLOBAL_CONTENT_VERSION any_version
                                 WHERE any_version.CONTENT_TYPE = 'QUESTION'
                                   AND any_version.CONTENT_ID = q.QUESTION_ID
                            )
                            OR EXISTS (
                                SELECT 1 FROM GLOBAL_CONTENT_VERSION published
                                 WHERE published.CONTENT_TYPE = 'QUESTION'
                                   AND published.CONTENT_ID = q.QUESTION_ID
                                   AND published.EDITORIAL_STATUS = 'PUBLISHED'
                            )
                        )
                    )
               )
               AND (:technologyId IS NULL OR q.TECHNOLOGY_ID = :technologyId)
               AND (
                    :categoryId IS NULL
                    OR EXISTS (
                        SELECT 1 FROM QUESTION_CATEGORY_RELATION relation
                         WHERE relation.QUESTION_ID = q.QUESTION_ID
                           AND relation.CATEGORY_ID = :categoryId
                    )
               )
             ORDER BY DBMS_RANDOM.VALUE
            """, nativeQuery = true)
    List<QuestionJpaEntity> findRandomEligible(@Param("organizationId") Long organizationId,
            @Param("technologyId") Long technologyId, @Param("categoryId") Long categoryId, Pageable pageable);
    @Query(value = """
            SELECT q.*
              FROM QUESTION q
             WHERE q.QUESTION_ID IN (:questionIds)
               AND q.STATUS = 'ACTIVE'
               AND q.TYPE_CODE IN ('SINGLE_CHOICE','MULTIPLE_CHOICE','TRUE_FALSE')
               AND (
                    (q.CONTENT_SCOPE = 'ORGANIZATION' AND q.OWNER_ORGANIZATION_ID = :organizationId)
                    OR (
                        q.CONTENT_SCOPE = 'GLOBAL'
                        AND (
                            NVL(q.AVAILABILITY_MODE, 'NONE') = 'GLOBAL'
                            OR (
                                NVL(q.AVAILABILITY_MODE, 'NONE') = 'SELECTED_ORGANIZATIONS'
                                AND EXISTS (
                                SELECT 1 FROM QUESTION_ORGANIZATION_AVAILABILITY availability
                                 WHERE availability.QUESTION_ID = q.QUESTION_ID
                                   AND availability.ORGANIZATION_ID = :organizationId
                                   AND availability.STATUS = 'ACTIVE'
                                )
                            )
                        )
                        AND (
                            NOT EXISTS (
                                SELECT 1 FROM GLOBAL_CONTENT_VERSION any_version
                                 WHERE any_version.CONTENT_TYPE = 'QUESTION'
                                   AND any_version.CONTENT_ID = q.QUESTION_ID
                            )
                            OR EXISTS (
                                SELECT 1 FROM GLOBAL_CONTENT_VERSION published
                                 WHERE published.CONTENT_TYPE = 'QUESTION'
                                   AND published.CONTENT_ID = q.QUESTION_ID
                                   AND published.EDITORIAL_STATUS = 'PUBLISHED'
                            )
                        )
                    )
               )
             ORDER BY DBMS_RANDOM.VALUE
            """, nativeQuery = true)
    List<QuestionJpaEntity> findRandomEligibleByIds(@Param("organizationId") Long organizationId,
            @Param("questionIds") List<Long> questionIds, Pageable pageable);

}
