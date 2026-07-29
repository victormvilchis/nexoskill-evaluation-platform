package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
    void reportsQuestionAvailableWhenNoActiveDependencyExists() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), anyMap(), eq(Integer.class))).thenReturn(0);

        assertThat(new OracleQuestionUsageChecker(jdbc).isUsedByActiveExam(10L)).isFalse();
    }
}
