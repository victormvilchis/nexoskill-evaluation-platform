package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import com.nexoskill.evaluation.questionbank.application.port.out.QuestionUsageChecker;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Verifica usos operativos y retira relaciones directas de una pregunta.
 * Los pools por categoría no almacenan la pregunta y solo consumen registros ACTIVE.
 */
@Component
public class OracleQuestionUsageChecker implements QuestionUsageChecker {
    private final NamedParameterJdbcTemplate jdbc;

    public OracleQuestionUsageChecker(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isUsedByActiveExam(Long questionId) {
        if (questionId == null) return false;
        Integer count = jdbc.queryForObject("""
            SELECT (
                SELECT COUNT(*)
                  FROM FORM_QUESTION fixed_question
                  JOIN FORM_SECTION section_value
                    ON section_value.SECTION_ID = fixed_question.SECTION_ID
                  JOIN EVALUATION_FORM form_value
                    ON form_value.FORM_ID = section_value.FORM_ID
                 WHERE fixed_question.QUESTION_ID = :questionId
                   AND form_value.STATUS = 'ACTIVE'
            ) + (
                SELECT COUNT(*)
                  FROM COLLECTION_QUESTION_RELATION collection_question
                  JOIN FORM_QUESTION_POOL pool_value
                    ON pool_value.COLLECTION_ID = collection_question.COLLECTION_ID
                   AND pool_value.SOURCE_TYPE = 'COLLECTION'
                  JOIN FORM_SECTION section_value
                    ON section_value.SECTION_ID = pool_value.SECTION_ID
                  JOIN EVALUATION_FORM form_value
                    ON form_value.FORM_ID = section_value.FORM_ID
                 WHERE collection_question.QUESTION_ID = :questionId
                   AND form_value.STATUS = 'ACTIVE'
            ) + (
                SELECT COUNT(*)
                  FROM QUESTION_CATEGORY_RELATION question_category
                  JOIN FORM_QUESTION_POOL pool_value
                    ON pool_value.CATEGORY_ID = question_category.CATEGORY_ID
                   AND pool_value.SOURCE_TYPE = 'CATEGORY'
                  JOIN FORM_SECTION section_value
                    ON section_value.SECTION_ID = pool_value.SECTION_ID
                  JOIN EVALUATION_FORM form_value
                    ON form_value.FORM_ID = section_value.FORM_ID
                 WHERE question_category.QUESTION_ID = :questionId
                   AND form_value.STATUS = 'ACTIVE'
            ) AS ACTIVE_USAGE_COUNT
              FROM DUAL
            """, Map.of("questionId", questionId), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public DetachmentResult detachFromForms(Long questionId) {
        if (questionId == null) return new DetachmentResult(0, 0);
        Map<String, Object> params = Map.of("questionId", questionId);
        List<Long> affectedSections = jdbc.queryForList(
                "SELECT DISTINCT SECTION_ID FROM FORM_QUESTION WHERE QUESTION_ID = :questionId",
                params, Long.class);
        int fixedRelations = jdbc.update(
                "DELETE FROM FORM_QUESTION WHERE QUESTION_ID = :questionId", params);
        normalizeQuestionOrder(affectedSections);
        int collectionRelations = jdbc.update(
                "DELETE FROM COLLECTION_QUESTION_RELATION WHERE QUESTION_ID = :questionId", params);
        return new DetachmentResult(fixedRelations, collectionRelations);
    }

    private void normalizeQuestionOrder(List<Long> sectionIds) {
        if (sectionIds == null || sectionIds.isEmpty()) return;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("sectionIds", sectionIds);
        jdbc.update("""
            UPDATE FORM_QUESTION
               SET QUESTION_ORDER = -FORM_QUESTION_ID
             WHERE SECTION_ID IN (:sectionIds)
            """, params);
        jdbc.update("""
            MERGE INTO FORM_QUESTION target
            USING (
                SELECT FORM_QUESTION_ID,
                       ROW_NUMBER() OVER (
                           PARTITION BY SECTION_ID
                           ORDER BY QUESTION_ORDER, FORM_QUESTION_ID
                       ) AS NORMALIZED_ORDER
                  FROM FORM_QUESTION
                 WHERE SECTION_ID IN (:sectionIds)
            ) source
               ON (target.FORM_QUESTION_ID = source.FORM_QUESTION_ID)
             WHEN MATCHED THEN UPDATE
                  SET target.QUESTION_ORDER = source.NORMALIZED_ORDER
            """, params);
    }

    @Override
    public boolean hasHistoricalActivity(Long questionId) {
        if (questionId == null) return false;
        Integer count = jdbc.queryForObject("""
            SELECT (
                SELECT COUNT(*)
                  FROM STUDENT_PRACTICE_QUESTION
                 WHERE QUESTION_ID = :questionId
            ) + (
                SELECT COUNT(*)
                  FROM STUDENT_EVALUATION_QUESTION
                 WHERE QUESTION_ID = :questionId
            ) AS HISTORICAL_USAGE_COUNT
              FROM DUAL
            """, Map.of("questionId", questionId), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public void deletePermanently(Long questionId) {
        if (questionId == null) return;
        Map<String, Object> params = Map.of("questionId", questionId);

        jdbc.update("""
            UPDATE QUESTION
               SET SOURCE_GLOBAL_ID = NULL,
                   SOURCE_GLOBAL_VERSION = NULL,
                   LAST_SYNCHRONIZED_AT = NULL,
                   SYNC_STATUS = 'NOT_LINKED'
             WHERE SOURCE_GLOBAL_ID = :questionId
            """, params);
        jdbc.update("""
            UPDATE QUESTION
               SET SOURCE_ORGANIZATION_ID = NULL,
                   SOURCE_ORGANIZATION_QUESTION_ID = NULL,
                   SOURCE_ORGANIZATION_VERSION = NULL,
                   CLONED_TO_GLOBAL_AT = NULL,
                   CLONED_TO_GLOBAL_BY = NULL,
                   LAST_SYNCHRONIZED_AT = NULL,
                   SYNC_STATUS = 'NOT_LINKED'
             WHERE SOURCE_ORGANIZATION_QUESTION_ID = :questionId
            """, params);
        jdbc.update("""
            UPDATE CONTENT_DISTRIBUTION_RESULT
               SET GRANT_ID = NULL
             WHERE GRANT_ID IN (
                 SELECT GRANT_ID
                   FROM ORGANIZATION_GLOBAL_CONTENT_GRANT
                  WHERE CONTENT_TYPE = 'QUESTION'
                    AND GLOBAL_CONTENT_ID = :questionId
             )
            """, params);
        jdbc.update("""
            DELETE FROM ORGANIZATION_GLOBAL_CONTENT_GRANT
             WHERE CONTENT_TYPE = 'QUESTION'
               AND GLOBAL_CONTENT_ID = :questionId
            """, params);
        jdbc.update("""
            DELETE FROM CONTENT_REPLICATION_LINK
             WHERE CONTENT_TYPE = 'QUESTION'
               AND (SOURCE_GLOBAL_CONTENT_ID = :questionId OR TARGET_CONTENT_ID = :questionId)
            """, params);
        jdbc.update("DELETE FROM QUESTION_ORGANIZATION_AVAILABILITY WHERE QUESTION_ID = :questionId", params);
        jdbc.update("DELETE FROM QUESTION_TAG_RELATION WHERE QUESTION_ID = :questionId", params);
        jdbc.update("DELETE FROM QUESTION_CATEGORY_RELATION WHERE QUESTION_ID = :questionId", params);
        jdbc.update("UPDATE QUESTION SET CURRENT_VERSION_ID = NULL WHERE QUESTION_ID = :questionId", params);
        jdbc.update("DELETE FROM QUESTION_OPTION WHERE QUESTION_ID = :questionId", params);
        jdbc.update("DELETE FROM QUESTION_VERSION WHERE QUESTION_ID = :questionId", params);
        int deleted = jdbc.update("DELETE FROM QUESTION WHERE QUESTION_ID = :questionId", params);
        if (deleted != 1) {
            throw new IllegalStateException("No fue posible eliminar físicamente la pregunta solicitada.");
        }
    }
}
