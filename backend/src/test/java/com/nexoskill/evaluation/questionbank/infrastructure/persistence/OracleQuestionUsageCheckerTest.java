package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class OracleQuestionUsageCheckerTest {

    @Test
    void reportsQuestionUsedWhenAnActiveDependencyExists() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), anyMap(), eq(Integer.class))).thenReturn(2);

        assertThat(new OracleQuestionUsageChecker(jdbc).isUsedByActiveExam(10L)).isTrue();
    }

    @Test
    void removesOnlyDirectFormAndCollectionRelations() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.update(eq("DELETE FROM FORM_QUESTION WHERE QUESTION_ID = :questionId"), anyMap()))
                .thenReturn(2);
        when(jdbc.update(eq("DELETE FROM COLLECTION_QUESTION_RELATION WHERE QUESTION_ID = :questionId"), anyMap()))
                .thenReturn(1);

        var result = new OracleQuestionUsageChecker(jdbc).detachFromForms(10L);

        assertThat(result.fixedFormRelations()).isEqualTo(2);
        assertThat(result.collectionRelations()).isEqualTo(1);
        verify(jdbc).update(eq("DELETE FROM FORM_QUESTION WHERE QUESTION_ID = :questionId"), anyMap());
        verify(jdbc).update(eq("DELETE FROM COLLECTION_QUESTION_RELATION WHERE QUESTION_ID = :questionId"), anyMap());
    }

    @Test
    void reportsQuestionAvailableWhenNoActiveDependencyExists() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), anyMap(), eq(Integer.class))).thenReturn(0);

        assertThat(new OracleQuestionUsageChecker(jdbc).isUsedByActiveExam(10L)).isFalse();
    }
}
