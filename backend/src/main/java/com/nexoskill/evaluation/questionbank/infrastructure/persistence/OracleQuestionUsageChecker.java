package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import com.nexoskill.evaluation.questionbank.application.port.out.QuestionUsageChecker;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Verifica usos operativos y retira relaciones directas cuando una pregunta
 * se elimina lógicamente. Los pools por categoría se conservan porque no
 * almacenan una referencia directa a la pregunta y solo consumen preguntas activas.
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
        int fixedRelations = jdbc.update(
                "DELETE FROM FORM_QUESTION WHERE QUESTION_ID = :questionId", params);
        int collectionRelations = jdbc.update(
                "DELETE FROM COLLECTION_QUESTION_RELATION WHERE QUESTION_ID = :questionId", params);
        return new DetachmentResult(fixedRelations, collectionRelations);
    }

}
