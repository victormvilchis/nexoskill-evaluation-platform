package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import com.nexoskill.evaluation.questionbank.domain.model.CatalogStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class QuestionCategoryJpaEntityTest {

	private static final Instant NOW = Instant.parse("2026-07-26T18:00:00Z");

	@Test
	void shouldPreserveCategoryAndApplyTheAllowedLifecycle() {
		QuestionCategoryJpaEntity category = QuestionCategoryJpaEntity.create("00000000-0000-0000-0000-000000000010",
				"JAVA", "Java", null, ContentScope.GLOBAL, null, 1L, NOW);

		assertThat(category.getStatus()).isEqualTo(CatalogStatus.ACTIVE);

		category.deactivate(2L, NOW.plusSeconds(60));
		assertThat(category.getStatus()).isEqualTo(CatalogStatus.INACTIVE);

		category.activate(2L, NOW.plusSeconds(120));
		assertThat(category.getStatus()).isEqualTo(CatalogStatus.ACTIVE);
	}

	@Test
	void shouldRequireAnOrganizationForOrganizationalCategories() {
		assertThatThrownBy(() -> QuestionCategoryJpaEntity.create("00000000-0000-0000-0000-000000000011", "SPRING",
				"Spring", null, ContentScope.ORGANIZATION, null, 1L, NOW)).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Una categoría organizacional requiere organización propietaria.");
	}

	@Test
	void shouldNotReactivateADeletedCategory() {
		QuestionCategoryJpaEntity category = QuestionCategoryJpaEntity.create("00000000-0000-0000-0000-000000000012",
				"ORACLE", "Oracle", null, ContentScope.ORGANIZATION, 25L, 1L, NOW);
		category.deactivate(2L, NOW.plusSeconds(60));
		category.softDelete(2L, NOW.plusSeconds(120));

		assertThat(category.getStatus()).isEqualTo(CatalogStatus.DELETED);
		assertThatThrownBy(() -> category.activate(2L, NOW.plusSeconds(180))).isInstanceOf(IllegalStateException.class);
	}
}
