package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import com.nexoskill.evaluation.questionbank.domain.model.QuestionStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataQuestionRepository extends JpaRepository<QuestionJpaEntity, Long> {
    Optional<QuestionJpaEntity> findByPublicId(String id);
    List<QuestionJpaEntity> findAllByPublicIdIn(Collection<String> ids);
    List<QuestionJpaEntity> findAllByStatus(QuestionStatus status);

    @Query(value = """
            SELECT DISTINCT q.*
              FROM QUESTION q
              LEFT JOIN QUESTION_OPTION option_value
                ON option_value.QUESTION_ID = q.QUESTION_ID
             WHERE q.PROMPT_MEDIA_ID = :mediaId
                OR option_value.MEDIA_ID = :mediaId
                OR option_value.MATCH_MEDIA_ID = :mediaId
            """, nativeQuery = true)
    List<QuestionJpaEntity> findAllUsingMedia(@Param("mediaId") Long mediaId);

    @Query(value = """
            SELECT q.*
              FROM QUESTION q
             WHERE q.STATUS = :status
               AND EXISTS (
                    SELECT 1
                      FROM QUESTION_CATEGORY_RELATION qr
                     WHERE qr.QUESTION_ID = q.QUESTION_ID
                       AND qr.CATEGORY_ID IN (:ids)
               )
             ORDER BY q.CREATED_AT DESC
            """, nativeQuery = true)
    List<QuestionJpaEntity> findByStatusAndAnyCategoryIdIn(@Param("status") String status,
            @Param("ids") Collection<Long> ids);

    @Query(value = """
            SELECT q.*
              FROM QUESTION q
             WHERE q.PUBLIC_ID = :id
               FOR UPDATE
            """, nativeQuery = true)
    Optional<QuestionJpaEntity> findByPublicIdForUpdate(@Param("id") String id);

    @Query(value = """
            SELECT q.*
              FROM QUESTION q
             WHERE ((:status IS NULL AND q.STATUS <> 'DELETED') OR q.STATUS = :status)
               AND (:typeCode IS NULL OR q.TYPE_CODE = :typeCode)
               AND (
                    :categoryPublicId IS NULL
                    OR EXISTS (
                        SELECT 1
                          FROM QUESTION_CATEGORY_RELATION qr
                          JOIN QUESTION_CATEGORY c ON c.CATEGORY_ID = qr.CATEGORY_ID
                         WHERE qr.QUESTION_ID = q.QUESTION_ID
                           AND c.PUBLIC_ID = :categoryPublicId
                    )
               )
               AND (
                    (:globalScope = 1 AND q.CONTENT_SCOPE = 'GLOBAL')
                    OR (
                        :globalScope = 0
                        AND q.CONTENT_SCOPE = 'ORGANIZATION'
                        AND q.OWNER_ORGANIZATION_ID = :organizationId
                    )
                    OR (
                        :globalScope = 0
                        AND q.CONTENT_SCOPE = 'GLOBAL'
                        AND (
                            NOT EXISTS (
                                SELECT 1
                                  FROM GLOBAL_CONTENT_VERSION gcv_any
                                 WHERE gcv_any.CONTENT_TYPE = 'QUESTION'
                                   AND gcv_any.CONTENT_ID = q.QUESTION_ID
                            )
                            OR EXISTS (
                                SELECT 1
                                  FROM GLOBAL_CONTENT_VERSION gcv_published
                                 WHERE gcv_published.CONTENT_TYPE = 'QUESTION'
                                   AND gcv_published.CONTENT_ID = q.QUESTION_ID
                                   AND gcv_published.EDITORIAL_STATUS = 'PUBLISHED'
                            )
                        )
                        AND (
                            :allGlobalContent = 1
                            OR EXISTS (
                                SELECT 1
                                  FROM ORGANIZATION_GLOBAL_CONTENT_GRANT gcg
                                 WHERE gcg.ORGANIZATION_ID = :organizationId
                                   AND gcg.CONTENT_TYPE = 'QUESTION'
                                   AND gcg.GLOBAL_CONTENT_ID = q.QUESTION_ID
                                   AND gcg.STATUS = 'ACTIVE'
                                   AND gcg.DISTRIBUTION_MODE = 'GLOBAL_REFERENCE'
                                   AND (gcg.AVAILABLE_FROM IS NULL OR gcg.AVAILABLE_FROM <= SYSTIMESTAMP)
                                   AND (gcg.EXPIRES_AT IS NULL OR gcg.EXPIRES_AT > SYSTIMESTAMP)
                            )
                        )
                    )
               )
             ORDER BY q.CREATED_AT DESC
            """,
            countQuery = """
            SELECT COUNT(*)
              FROM QUESTION q
             WHERE ((:status IS NULL AND q.STATUS <> 'DELETED') OR q.STATUS = :status)
               AND (:typeCode IS NULL OR q.TYPE_CODE = :typeCode)
               AND (
                    :categoryPublicId IS NULL
                    OR EXISTS (
                        SELECT 1
                          FROM QUESTION_CATEGORY_RELATION qr
                          JOIN QUESTION_CATEGORY c ON c.CATEGORY_ID = qr.CATEGORY_ID
                         WHERE qr.QUESTION_ID = q.QUESTION_ID
                           AND c.PUBLIC_ID = :categoryPublicId
                    )
               )
               AND (
                    (:globalScope = 1 AND q.CONTENT_SCOPE = 'GLOBAL')
                    OR (
                        :globalScope = 0
                        AND q.CONTENT_SCOPE = 'ORGANIZATION'
                        AND q.OWNER_ORGANIZATION_ID = :organizationId
                    )
                    OR (
                        :globalScope = 0
                        AND q.CONTENT_SCOPE = 'GLOBAL'
                        AND (
                            NOT EXISTS (
                                SELECT 1
                                  FROM GLOBAL_CONTENT_VERSION gcv_any
                                 WHERE gcv_any.CONTENT_TYPE = 'QUESTION'
                                   AND gcv_any.CONTENT_ID = q.QUESTION_ID
                            )
                            OR EXISTS (
                                SELECT 1
                                  FROM GLOBAL_CONTENT_VERSION gcv_published
                                 WHERE gcv_published.CONTENT_TYPE = 'QUESTION'
                                   AND gcv_published.CONTENT_ID = q.QUESTION_ID
                                   AND gcv_published.EDITORIAL_STATUS = 'PUBLISHED'
                            )
                        )
                        AND (
                            :allGlobalContent = 1
                            OR EXISTS (
                                SELECT 1
                                  FROM ORGANIZATION_GLOBAL_CONTENT_GRANT gcg
                                 WHERE gcg.ORGANIZATION_ID = :organizationId
                                   AND gcg.CONTENT_TYPE = 'QUESTION'
                                   AND gcg.GLOBAL_CONTENT_ID = q.QUESTION_ID
                                   AND gcg.STATUS = 'ACTIVE'
                                   AND gcg.DISTRIBUTION_MODE = 'GLOBAL_REFERENCE'
                                   AND (gcg.AVAILABLE_FROM IS NULL OR gcg.AVAILABLE_FROM <= SYSTIMESTAMP)
                                   AND (gcg.EXPIRES_AT IS NULL OR gcg.EXPIRES_AT > SYSTIMESTAMP)
                            )
                        )
                    )
               )
            """, nativeQuery = true)
    Page<QuestionJpaEntity> searchWithoutText(
            @Param("status") String status,
            @Param("typeCode") String typeCode,
            @Param("categoryPublicId") String categoryPublicId,
            @Param("globalScope") int globalScope,
            @Param("organizationId") Long organizationId,
            @Param("allGlobalContent") int allGlobalContent,
            Pageable pageable);

    @Query(value = """
            SELECT q.*
              FROM QUESTION q
             WHERE (
                    LOWER(DBMS_LOB.SUBSTR(q.STATEMENT_TEXT, 4000, 1)) LIKE '%' || :query || '%'
                    OR LOWER(DBMS_LOB.SUBSTR(q.CODE_CONTENT, 4000, 1)) LIKE '%' || :query || '%'
                    OR EXISTS (
                        SELECT 1
                          FROM QUESTION_OPTION qo_text
                         WHERE qo_text.QUESTION_ID = q.QUESTION_ID
                           AND (
                                LOWER(DBMS_LOB.SUBSTR(qo_text.OPTION_TEXT, 4000, 1)) LIKE '%' || :query || '%'
                                OR LOWER(DBMS_LOB.SUBSTR(qo_text.MATCH_TEXT, 4000, 1)) LIKE '%' || :query || '%'
                                OR LOWER(DBMS_LOB.SUBSTR(qo_text.FEEDBACK_TEXT, 4000, 1)) LIKE '%' || :query || '%'
                           )
                    )
                    OR EXISTS (
                        SELECT 1
                          FROM QUESTION_CATEGORY_RELATION qr_text
                          JOIN QUESTION_CATEGORY c_text ON c_text.CATEGORY_ID = qr_text.CATEGORY_ID
                         WHERE qr_text.QUESTION_ID = q.QUESTION_ID
                           AND LOWER(c_text.CATEGORY_NAME) LIKE '%' || :query || '%'
                    )
               )
               AND ((:status IS NULL AND q.STATUS <> 'DELETED') OR q.STATUS = :status)
               AND (:typeCode IS NULL OR q.TYPE_CODE = :typeCode)
               AND (
                    :categoryPublicId IS NULL
                    OR EXISTS (
                        SELECT 1
                          FROM QUESTION_CATEGORY_RELATION qr
                          JOIN QUESTION_CATEGORY c ON c.CATEGORY_ID = qr.CATEGORY_ID
                         WHERE qr.QUESTION_ID = q.QUESTION_ID
                           AND c.PUBLIC_ID = :categoryPublicId
                    )
               )
               AND (
                    (:globalScope = 1 AND q.CONTENT_SCOPE = 'GLOBAL')
                    OR (
                        :globalScope = 0
                        AND q.CONTENT_SCOPE = 'ORGANIZATION'
                        AND q.OWNER_ORGANIZATION_ID = :organizationId
                    )
                    OR (
                        :globalScope = 0
                        AND q.CONTENT_SCOPE = 'GLOBAL'
                        AND (
                            NOT EXISTS (
                                SELECT 1
                                  FROM GLOBAL_CONTENT_VERSION gcv_any
                                 WHERE gcv_any.CONTENT_TYPE = 'QUESTION'
                                   AND gcv_any.CONTENT_ID = q.QUESTION_ID
                            )
                            OR EXISTS (
                                SELECT 1
                                  FROM GLOBAL_CONTENT_VERSION gcv_published
                                 WHERE gcv_published.CONTENT_TYPE = 'QUESTION'
                                   AND gcv_published.CONTENT_ID = q.QUESTION_ID
                                   AND gcv_published.EDITORIAL_STATUS = 'PUBLISHED'
                            )
                        )
                        AND (
                            :allGlobalContent = 1
                            OR EXISTS (
                                SELECT 1
                                  FROM ORGANIZATION_GLOBAL_CONTENT_GRANT gcg
                                 WHERE gcg.ORGANIZATION_ID = :organizationId
                                   AND gcg.CONTENT_TYPE = 'QUESTION'
                                   AND gcg.GLOBAL_CONTENT_ID = q.QUESTION_ID
                                   AND gcg.STATUS = 'ACTIVE'
                                   AND gcg.DISTRIBUTION_MODE = 'GLOBAL_REFERENCE'
                                   AND (gcg.AVAILABLE_FROM IS NULL OR gcg.AVAILABLE_FROM <= SYSTIMESTAMP)
                                   AND (gcg.EXPIRES_AT IS NULL OR gcg.EXPIRES_AT > SYSTIMESTAMP)
                            )
                        )
                    )
               )
             ORDER BY q.CREATED_AT DESC
            """,
            countQuery = """
            SELECT COUNT(*)
              FROM QUESTION q
             WHERE (
                    LOWER(DBMS_LOB.SUBSTR(q.STATEMENT_TEXT, 4000, 1)) LIKE '%' || :query || '%'
                    OR LOWER(DBMS_LOB.SUBSTR(q.CODE_CONTENT, 4000, 1)) LIKE '%' || :query || '%'
                    OR EXISTS (
                        SELECT 1
                          FROM QUESTION_OPTION qo_text
                         WHERE qo_text.QUESTION_ID = q.QUESTION_ID
                           AND (
                                LOWER(DBMS_LOB.SUBSTR(qo_text.OPTION_TEXT, 4000, 1)) LIKE '%' || :query || '%'
                                OR LOWER(DBMS_LOB.SUBSTR(qo_text.MATCH_TEXT, 4000, 1)) LIKE '%' || :query || '%'
                                OR LOWER(DBMS_LOB.SUBSTR(qo_text.FEEDBACK_TEXT, 4000, 1)) LIKE '%' || :query || '%'
                           )
                    )
                    OR EXISTS (
                        SELECT 1
                          FROM QUESTION_CATEGORY_RELATION qr_text
                          JOIN QUESTION_CATEGORY c_text ON c_text.CATEGORY_ID = qr_text.CATEGORY_ID
                         WHERE qr_text.QUESTION_ID = q.QUESTION_ID
                           AND LOWER(c_text.CATEGORY_NAME) LIKE '%' || :query || '%'
                    )
               )
               AND ((:status IS NULL AND q.STATUS <> 'DELETED') OR q.STATUS = :status)
               AND (:typeCode IS NULL OR q.TYPE_CODE = :typeCode)
               AND (
                    :categoryPublicId IS NULL
                    OR EXISTS (
                        SELECT 1
                          FROM QUESTION_CATEGORY_RELATION qr
                          JOIN QUESTION_CATEGORY c ON c.CATEGORY_ID = qr.CATEGORY_ID
                         WHERE qr.QUESTION_ID = q.QUESTION_ID
                           AND c.PUBLIC_ID = :categoryPublicId
                    )
               )
               AND (
                    (:globalScope = 1 AND q.CONTENT_SCOPE = 'GLOBAL')
                    OR (
                        :globalScope = 0
                        AND q.CONTENT_SCOPE = 'ORGANIZATION'
                        AND q.OWNER_ORGANIZATION_ID = :organizationId
                    )
                    OR (
                        :globalScope = 0
                        AND q.CONTENT_SCOPE = 'GLOBAL'
                        AND (
                            NOT EXISTS (
                                SELECT 1
                                  FROM GLOBAL_CONTENT_VERSION gcv_any
                                 WHERE gcv_any.CONTENT_TYPE = 'QUESTION'
                                   AND gcv_any.CONTENT_ID = q.QUESTION_ID
                            )
                            OR EXISTS (
                                SELECT 1
                                  FROM GLOBAL_CONTENT_VERSION gcv_published
                                 WHERE gcv_published.CONTENT_TYPE = 'QUESTION'
                                   AND gcv_published.CONTENT_ID = q.QUESTION_ID
                                   AND gcv_published.EDITORIAL_STATUS = 'PUBLISHED'
                            )
                        )
                        AND (
                            :allGlobalContent = 1
                            OR EXISTS (
                                SELECT 1
                                  FROM ORGANIZATION_GLOBAL_CONTENT_GRANT gcg
                                 WHERE gcg.ORGANIZATION_ID = :organizationId
                                   AND gcg.CONTENT_TYPE = 'QUESTION'
                                   AND gcg.GLOBAL_CONTENT_ID = q.QUESTION_ID
                                   AND gcg.STATUS = 'ACTIVE'
                                   AND gcg.DISTRIBUTION_MODE = 'GLOBAL_REFERENCE'
                                   AND (gcg.AVAILABLE_FROM IS NULL OR gcg.AVAILABLE_FROM <= SYSTIMESTAMP)
                                   AND (gcg.EXPIRES_AT IS NULL OR gcg.EXPIRES_AT > SYSTIMESTAMP)
                            )
                        )
                    )
               )
            """, nativeQuery = true)
    Page<QuestionJpaEntity> searchWithText(
            @Param("query") String query,
            @Param("status") String status,
            @Param("typeCode") String typeCode,
            @Param("categoryPublicId") String categoryPublicId,
            @Param("globalScope") int globalScope,
            @Param("organizationId") Long organizationId,
            @Param("allGlobalContent") int allGlobalContent,
            Pageable pageable);

    @Query(value = """
            SELECT membership.QUESTION_ID,
                   membership.FORM_PUBLIC_ID,
                   membership.FORM_TITLE,
                   membership.COLLECTION_PUBLIC_ID,
                   membership.COLLECTION_NAME
              FROM (
                    SELECT fq.QUESTION_ID,
                           f.PUBLIC_ID AS FORM_PUBLIC_ID,
                           f.TITLE AS FORM_TITLE,
                           lc.PUBLIC_ID AS COLLECTION_PUBLIC_ID,
                           lc.COLLECTION_NAME
                      FROM FORM_QUESTION fq
                      JOIN FORM_SECTION fs ON fs.SECTION_ID = fq.SECTION_ID
                      JOIN EVALUATION_FORM f ON f.FORM_ID = fs.FORM_ID
                      LEFT JOIN LEARNING_COLLECTION_LEVEL lcl ON lcl.FORM_ID = f.FORM_ID
                      LEFT JOIN LEARNING_COLLECTION lc ON lc.COLLECTION_ID = lcl.COLLECTION_ID
                     WHERE fq.QUESTION_ID IN (:ids)
                    UNION
                    SELECT qcr.QUESTION_ID,
                           f.PUBLIC_ID AS FORM_PUBLIC_ID,
                           f.TITLE AS FORM_TITLE,
                           lc.PUBLIC_ID AS COLLECTION_PUBLIC_ID,
                           lc.COLLECTION_NAME
                      FROM FORM_QUESTION_POOL fp
                      JOIN FORM_SECTION fs ON fs.SECTION_ID = fp.SECTION_ID
                      JOIN EVALUATION_FORM f ON f.FORM_ID = fs.FORM_ID
                      JOIN QUESTION_CATEGORY_RELATION qcr
                        ON fp.SOURCE_TYPE = 'CATEGORY'
                       AND qcr.CATEGORY_ID = fp.CATEGORY_ID
                      JOIN QUESTION q_pool
                        ON q_pool.QUESTION_ID = qcr.QUESTION_ID
                       AND q_pool.STATUS <> 'DELETED'
                      LEFT JOIN LEARNING_COLLECTION_LEVEL lcl ON lcl.FORM_ID = f.FORM_ID
                      LEFT JOIN LEARNING_COLLECTION lc ON lc.COLLECTION_ID = lcl.COLLECTION_ID
                     WHERE qcr.QUESTION_ID IN (:ids)
              ) membership
             ORDER BY membership.QUESTION_ID, membership.FORM_TITLE, membership.COLLECTION_NAME
            """, nativeQuery = true)
    List<Object[]> findMemberships(@Param("ids") Collection<Long> ids);

    @Query(value = """
            SELECT COUNT(*)
              FROM QUESTION_CATEGORY_RELATION qr
              JOIN QUESTION q ON q.QUESTION_ID = qr.QUESTION_ID
             WHERE qr.CATEGORY_ID = :id
               AND q.STATUS = 'ACTIVE'
            """, nativeQuery = true)
    long countActiveByCategory(@Param("id") Long id);

    @Query(value = """
            SELECT COUNT(*)
              FROM QUESTION_CATEGORY_RELATION qr
             WHERE qr.CATEGORY_ID = :id
            """, nativeQuery = true)
    long countAllByCategory(@Param("id") Long id);

	boolean existsBySourceGlobalIdAndOwnerOrganizationIdAndStatusNot(Long sourceGlobalId, Long ownerOrganizationId, com.nexoskill.evaluation.questionbank.domain.model.QuestionStatus status);
}
