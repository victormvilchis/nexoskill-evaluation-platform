package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataQuestionRepository
        extends JpaRepository<QuestionJpaEntity, Long> {

    Optional<QuestionJpaEntity> findByPublicId(String publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT q FROM QuestionJpaEntity q WHERE q.publicId = :publicId")
    Optional<QuestionJpaEntity> findByPublicIdForUpdate(
            @Param("publicId") String publicId
    );

    @Query(
            value = """
                    SELECT q.*
                    FROM QUESTION q
                    JOIN QUESTION_VERSION qv
                      ON q.CURRENT_VERSION_ID = qv.QUESTION_VERSION_ID
                    JOIN QUESTION_CATEGORY qc
                      ON q.CATEGORY_ID = qc.CATEGORY_ID
                    WHERE (
                        :query IS NULL
                        OR DBMS_LOB.INSTR(LOWER(qv.STATEMENT_TEXT), :query) > 0
                        OR LOWER(qc.CATEGORY_NAME) LIKE '%' || :query || '%'
                    )
                    AND (:status IS NULL OR q.STATUS = :status)
                    AND (:typeCode IS NULL OR q.TYPE_CODE = :typeCode)
                    AND (:difficultyCode IS NULL OR q.DIFFICULTY_CODE = :difficultyCode)
                    AND (
                        :categoryPublicId IS NULL
                        OR qc.PUBLIC_ID = :categoryPublicId
                    )
                    ORDER BY q.CREATED_AT DESC
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM QUESTION q
                    JOIN QUESTION_VERSION qv
                      ON q.CURRENT_VERSION_ID = qv.QUESTION_VERSION_ID
                    JOIN QUESTION_CATEGORY qc
                      ON q.CATEGORY_ID = qc.CATEGORY_ID
                    WHERE (
                        :query IS NULL
                        OR DBMS_LOB.INSTR(LOWER(qv.STATEMENT_TEXT), :query) > 0
                        OR LOWER(qc.CATEGORY_NAME) LIKE '%' || :query || '%'
                    )
                    AND (:status IS NULL OR q.STATUS = :status)
                    AND (:typeCode IS NULL OR q.TYPE_CODE = :typeCode)
                    AND (:difficultyCode IS NULL OR q.DIFFICULTY_CODE = :difficultyCode)
                    AND (
                        :categoryPublicId IS NULL
                        OR qc.PUBLIC_ID = :categoryPublicId
                    )
                    """,
            nativeQuery = true
    )
    Page<QuestionJpaEntity> search(
            @Param("query") String query,
            @Param("status") String status,
            @Param("typeCode") String typeCode,
            @Param("difficultyCode") String difficultyCode,
            @Param("categoryPublicId") String categoryPublicId,
            Pageable pageable
    );
}
