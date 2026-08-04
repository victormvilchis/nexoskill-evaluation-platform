package com.nexoskill.evaluation.students.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

class StudentImportReceiptStoreTest {
	private static final Instant NOW = Instant.parse("2026-08-02T04:00:00Z");

	private NamedParameterJdbcTemplate jdbc;
	private StudentImportReceiptStore store;

	@BeforeEach
	void setUp() {
		jdbc = mock(NamedParameterJdbcTemplate.class);
		store = new StudentImportReceiptStore(jdbc, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void reservesAConfirmationTokenBeforeApplyingRows() {
		when(jdbc.update(contains("INSERT INTO STUDENT_IMPORT_RECEIPT"),
				org.mockito.ArgumentMatchers.any(SqlParameterSource.class))).thenReturn(1);

		store.reserve("token-1", 20L, "sha", 15, 7L);

		ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
		verify(jdbc).update(contains("STATUS_CODE, APPLIED_BY, APPLIED_AT"), params.capture());
		assertThat(params.getValue().getValue("confirmationToken")).isEqualTo("token-1");
		assertThat(params.getValue().getValue("organizationId")).isEqualTo(20L);
		assertThat(params.getValue().getValue("totalRows")).isEqualTo(15);
	}

	@Test
	void translatesConcurrentDuplicateReservationIntoFunctionalError() {
		when(jdbc.update(anyString(), org.mockito.ArgumentMatchers.any(SqlParameterSource.class)))
				.thenThrow(new DuplicateKeyException("duplicate"));

		assertThatThrownBy(() -> store.reserve("token-1", 20L, "sha", 15, 7L)).isInstanceOfSatisfying(
				BusinessException.class,
				exception -> assertThat(exception.getCode()).isEqualTo("STUDENT_IMPORT_ALREADY_APPLIED"));
	}

	@Test
	void completesOnlyAProcessingReceipt() {
		when(jdbc.update(contains("STATUS_CODE = 'COMPLETED'"),
				org.mockito.ArgumentMatchers.any(SqlParameterSource.class))).thenReturn(1);

		store.complete("token-1", 7, 8, 0, 1);

		ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
		verify(jdbc).update(contains("AND STATUS_CODE = 'PROCESSING'"), params.capture());
		assertThat(params.getValue().getValue("created")).isEqualTo(7);
		assertThat(params.getValue().getValue("updated")).isEqualTo(8);
		assertThat(params.getValue().getValue("errors")).isEqualTo(1);
	}

	@Test
	void readsPersistentStatusAfterInMemoryPreviewIsGone() {
		when(jdbc.query(contains("SELECT STATUS_CODE"), org.mockito.ArgumentMatchers.anyMap(),
				org.mockito.ArgumentMatchers.any(RowMapper.class))).thenReturn(List.of("COMPLETED"));

		assertThat(store.status("token-1", 20L, 7L)).isEqualTo("COMPLETED");
	}
}
