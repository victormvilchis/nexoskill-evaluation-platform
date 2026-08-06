package com.nexoskill.evaluation.catalogs.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nexoskill.evaluation.catalogs.domain.CatalogType;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogDependencyQueriesTest {
	@Test
	void technologyUsageUsesOnlyPersistedStudentTechnologyColumn() {
		List<String> queries = CatalogDependencyQueries.forType(CatalogType.TECHNOLOGIES);

		assertTrue(queries.stream().anyMatch(query -> query.contains("TALENT_TECHNOLOGY_ID")));
		assertFalse(queries.stream().anyMatch(query -> query.contains("STUDENT WHERE TECHNOLOGY_ID")));
	}

	@Test
	void profileUsageDoesNotReferenceColumnsMissingFromStudent() {
		String professional = String.join(" ",
				CatalogDependencyQueries.forType(CatalogType.PROFESSIONAL_PROFILES));
		String technological = String.join(" ",
				CatalogDependencyQueries.forType(CatalogType.TECHNOLOGICAL_PROFILES));

		assertFalse(professional.contains("STUDENT WHERE PROFESSIONAL_PROFILE_ID"));
		assertFalse(technological.contains("STUDENT WHERE TECHNOLOGICAL_PROFILE_ID"));
		assertTrue(professional.contains("STUDENT_CERTIFICATION_PROFILE"));
		assertTrue(technological.contains("STUDENT_CERTIFICATION_PROFILE"));
	}
}
