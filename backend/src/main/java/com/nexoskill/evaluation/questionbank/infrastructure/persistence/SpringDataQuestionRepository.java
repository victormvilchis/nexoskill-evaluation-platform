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

    @Query(
            value = """
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
                    """,
            nativeQuery = true)
    List<QuestionJpaEntity> findByStatusAndAnyCategoryIdIn(
            @Param("status") String status,
            @Param("ids") Collection<Long> ids);

    /*
     * Oracle does not allow DISTINCT over CLOB columns. The previous JPQL
     * fetch join generated SELECT DISTINCT over the full QUESTION row because
     * QUESTION contains several CLOB fields. Lock only the root QUESTION row;
     * its associations are loaded normally inside the surrounding transaction.
     */
    @Query(
            value = """
                    SELECT q.*
                      FROM QUESTION q
                     WHERE q.PUBLIC_ID = :id
                       FOR UPDATE
                    """,
            nativeQuery = true)
    Optional<QuestionJpaEntity> findByPublicIdForUpdate(@Param("id") String id);

    /*
     * The previous query selected DISTINCT q.* while QUESTION contains CLOB columns.
     * Oracle cannot apply DISTINCT to a CLOB, which caused the question list endpoint
     * to fail. These queries keep QUESTION as the outer table and use EXISTS for
     * category filtering, avoiding DISTINCT over CLOB values entirely.
     */
    @Query(
            value = """
                    SELECT q.*
                      FROM QUESTION q
                     WHERE (:status IS NULL OR q.STATUS = :status)
                       AND (:typeCode IS NULL OR q.TYPE_CODE = :typeCode)
                       AND (:difficultyCode IS NULL OR q.DIFFICULTY_CODE = :difficultyCode)
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
                     ORDER BY q.CREATED_AT DESC
                    """,
            countQuery = """
                    SELECT COUNT(*)
                      FROM QUESTION q
                     WHERE (:status IS NULL OR q.STATUS = :status)
                       AND (:typeCode IS NULL OR q.TYPE_CODE = :typeCode)
                       AND (:difficultyCode IS NULL OR q.DIFFICULTY_CODE = :difficultyCode)
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
                    """,
            nativeQuery = true)
    Page<QuestionJpaEntity> searchWithoutText(
            @Param("status") String status,
            @Param("typeCode") String typeCode,
            @Param("difficultyCode") String difficultyCode,
            @Param("categoryPublicId") String categoryPublicId,
            Pageable pageable);

    @Query(
            value = """
                    SELECT q.*
                      FROM QUESTION q
                     WHERE (
                            LOWER(DBMS_LOB.SUBSTR(q.STATEMENT_TEXT, 4000, 1)) LIKE '%' || :query || '%'
                            OR EXISTS (
                                SELECT 1
                                  FROM QUESTION_CATEGORY_RELATION qr_text
                                  JOIN QUESTION_CATEGORY c_text ON c_text.CATEGORY_ID = qr_text.CATEGORY_ID
                                 WHERE qr_text.QUESTION_ID = q.QUESTION_ID
                                   AND LOWER(c_text.CATEGORY_NAME) LIKE '%' || :query || '%'
                            )
                       )
                       AND (:status IS NULL OR q.STATUS = :status)
                       AND (:typeCode IS NULL OR q.TYPE_CODE = :typeCode)
                       AND (:difficultyCode IS NULL OR q.DIFFICULTY_CODE = :difficultyCode)
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
                     ORDER BY q.CREATED_AT DESC
                    """,
            countQuery = """
                    SELECT COUNT(*)
                      FROM QUESTION q
                     WHERE (
                            LOWER(DBMS_LOB.SUBSTR(q.STATEMENT_TEXT, 4000, 1)) LIKE '%' || :query || '%'
                            OR EXISTS (
                                SELECT 1
                                  FROM QUESTION_CATEGORY_RELATION qr_text
                                  JOIN QUESTION_CATEGORY c_text ON c_text.CATEGORY_ID = qr_text.CATEGORY_ID
                                 WHERE qr_text.QUESTION_ID = q.QUESTION_ID
                                   AND LOWER(c_text.CATEGORY_NAME) LIKE '%' || :query || '%'
                            )
                       )
                       AND (:status IS NULL OR q.STATUS = :status)
                       AND (:typeCode IS NULL OR q.TYPE_CODE = :typeCode)
                       AND (:difficultyCode IS NULL OR q.DIFFICULTY_CODE = :difficultyCode)
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
                    """,
            nativeQuery = true)
    Page<QuestionJpaEntity> searchWithText(
            @Param("query") String query,
            @Param("status") String status,
            @Param("typeCode") String typeCode,
            @Param("difficultyCode") String difficultyCode,
            @Param("categoryPublicId") String categoryPublicId,
            Pageable pageable);

    @Query(
            value = """
                    SELECT COUNT(*)
                      FROM QUESTION_CATEGORY_RELATION qr
                      JOIN QUESTION q ON q.QUESTION_ID = qr.QUESTION_ID
                     WHERE qr.CATEGORY_ID = :id
                       AND q.STATUS = 'ACTIVE'
                    """,
            nativeQuery = true)
    long countActiveByCategory(@Param("id") Long id);
}
