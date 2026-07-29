package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class QuestionTagStoreTest {
    @Test
    void globalCopiesAlwaysPersistTagsWithoutAnOrganizationOwner() {
        NamedParameterJdbcTemplate jdbc = org.mockito.Mockito.mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(101L);
        QuestionTagStore store = new QuestionTagStore(jdbc);

        store.replace(22L, List.of("Polimorfismo"), ContentScope.GLOBAL, 999L, 7L,
                Instant.parse("2026-07-29T16:00:00Z"));

        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc, atLeastOnce()).update(anyString(), parameters.capture());
        MapSqlParameterSource ownership = parameters.getAllValues().stream()
                .filter(value -> value.hasValue("scope"))
                .findFirst()
                .orElseThrow();
        assertThat(ownership.getValue("scope")).isEqualTo("GLOBAL");
        assertThat(ownership.getValue("ownerOrganizationId")).isNull();
    }

    @Test
    void organizationalCopiesRequireAnOwner() {
        QuestionTagStore store = new QuestionTagStore(
                org.mockito.Mockito.mock(NamedParameterJdbcTemplate.class));

        assertThatThrownBy(() -> store.replace(22L, List.of("Java"), ContentScope.ORGANIZATION,
                null, 7L, Instant.parse("2026-07-29T16:00:00Z")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo("QUESTION_DUPLICATE_CONFIGURATION_INVALID"));
    }
}
