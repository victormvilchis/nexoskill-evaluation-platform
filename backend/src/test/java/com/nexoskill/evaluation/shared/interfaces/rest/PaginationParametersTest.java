package com.nexoskill.evaluation.shared.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class PaginationParametersTest {

	private static final Map<String, String> SORTS = Map.of("createdAt", "createdAt", "name", "name");

	@Test
	void usesTenRecordsAndStableSort() {
		var pageable = PaginationParameters.of(0, 10, "createdAt", "DESC", SORTS, "createdAt", Sort.Direction.DESC,
				"id");

		assertThat(pageable.getPageNumber()).isZero();
		assertThat(pageable.getPageSize()).isEqualTo(10);
		assertThat(pageable.getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
		assertThat(pageable.getSort().getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.DESC);
	}

	@Test
	void acceptsOnlyHomologatedSizes() {
		assertThatThrownBy(() -> PaginationParameters.validate(0, 20)).isInstanceOf(BusinessException.class)
				.hasMessageContaining("10, 25, 50 o 100");
	}

	@Test
	void rejectsNegativePages() {
		assertThatThrownBy(() -> PaginationParameters.validate(-1, 10)).isInstanceOf(BusinessException.class);
	}

	@Test
	void rejectsUnknownSorts() {
		assertThatThrownBy(() -> PaginationParameters.of(0, 10, "passwordHash", "ASC", SORTS, "createdAt",
				Sort.Direction.DESC, "id")).isInstanceOf(BusinessException.class);
	}
}
