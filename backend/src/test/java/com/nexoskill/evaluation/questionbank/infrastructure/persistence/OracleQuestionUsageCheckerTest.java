package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class OracleQuestionUsageCheckerTest {

    @Test
    void reportsQuestionUsedWhenAnActiveDependencyExists() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), anyMap(), eq(Integer.class))).thenReturn(2);

        assertThat(new OracleQuestionUsageChecker(jdbc).isUsedByActiveExam(10L)).isTrue();
    }

    @Test
    void removesDirectRelationsAndNormalizesRemainingQuestionOrder() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForList(contains("SELECT DISTINCT SECTION_ID"), anyMap(), eq(Long.class)))
                .thenReturn(List.of(50L));
        when(jdbc.update(eq("DELETE FROM FORM_QUESTION WHERE QUESTION_ID = :questionId"), anyMap()))
                .thenReturn(2);
        when(jdbc.update(eq("DELETE FROM COLLECTION_QUESTION_RELATION WHERE QUESTION_ID = :questionId"), anyMap()))
                .thenReturn(1);

        var result = new OracleQuestionUsageChecker(jdbc).detachFromForms(10L);

        assertThat(result.fixedFormRelations()).isEqualTo(2);
        assertThat(result.collectionRelations()).isEqualTo(1);
        verify(jdbc).update(contains("QUESTION_ORDER = -FORM_QUESTION_ID"),
                any(MapSqlParameterSource.class));
        verify(jdbc).update(contains("ROW_NUMBER() OVER"), any(MapSqlParameterSource.class));
    }

    @Test
    void physicallyDeletesQuestionAfterRemovingDependentRows() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.update(any(String.class), anyMap())).thenReturn(0);
        when(jdbc.update(eq("DELETE FROM QUESTION WHERE QUESTION_ID = :questionId"), anyMap()))
                .thenReturn(1);

        new OracleQuestionUsageChecker(jdbc).deletePermanently(10L);

        verify(jdbc).update(eq("DELETE FROM QUESTION WHERE QUESTION_ID = :questionId"), anyMap());
        verify(jdbc, atLeastOnce()).update(contains("QUESTION_ORGANIZATION_AVAILABILITY"), anyMap());
        verify(jdbc, atLeastOnce()).update(contains("QUESTION_OPTION"), anyMap());
    }

    @Test
    void reportsQuestionAvailableWhenNoActiveDependencyExists() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), anyMap(), eq(Integer.class))).thenReturn(0);

        assertThat(new OracleQuestionUsageChecker(jdbc).isUsedByActiveExam(10L)).isFalse();
    }
    @Test
    void detectsHistoricalPracticeOrEvaluationUsage() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForObject(contains("HISTORICAL_USAGE_COUNT"), anyMap(), eq(Integer.class))).thenReturn(1);

        assertThat(new OracleQuestionUsageChecker(jdbc).hasHistoricalActivity(10L)).isTrue();
    }

    @Test
    void reportsNoHistoricalUsageWhenQuestionHasNeverBeenAnswered() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForObject(contains("HISTORICAL_USAGE_COUNT"), anyMap(), eq(Integer.class))).thenReturn(0);

        assertThat(new OracleQuestionUsageChecker(jdbc).hasHistoricalActivity(10L)).isFalse();
    }

}
