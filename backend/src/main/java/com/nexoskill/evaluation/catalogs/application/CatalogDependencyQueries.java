package com.nexoskill.evaluation.catalogs.application;

import com.nexoskill.evaluation.catalogs.domain.CatalogType;
import java.util.List;

/**
 * Consultas de uso de catálogos basadas únicamente en relaciones persistidas
 * por el esquema actual. Mantenerlas separadas evita que una columna inválida
 * inutilice el conteo completo y permite identificar la relación que falla.
 */
final class CatalogDependencyQueries {
	private CatalogDependencyQueries() {
	}

	static List<String> forType(CatalogType type) {
		return switch (type) {
		case CATEGORIES -> List.of(
				"SELECT COUNT(*) FROM QUESTION WHERE CATEGORY_ID = :key",
				"SELECT COUNT(*) FROM QUESTION_CATEGORY_RELATION WHERE CATEGORY_ID = :key",
				"SELECT COUNT(*) FROM COLLECTION_CATEGORY_RELATION WHERE CATEGORY_ID = :key",
				"SELECT COUNT(*) FROM FORM_QUESTION_POOL WHERE CATEGORY_ID = :key",
				"SELECT COUNT(*) FROM QUESTION_CATEGORY WHERE SOURCE_GLOBAL_ID = :key");
		case TECHNOLOGIES -> List.of(
				"SELECT COUNT(*) FROM QUESTION WHERE TECHNOLOGY_ID = :key",
				"SELECT COUNT(*) FROM STUDENT WHERE TALENT_TECHNOLOGY_ID = :key",
				"SELECT COUNT(*) FROM STUDENT_CERTIFICATION_CYCLE WHERE TECHNOLOGY_ID = :key",
				"SELECT COUNT(*) FROM STUDENT_CERTIFICATION_PROFILE profile "
						+ "JOIN CERTIFICATION_TECHNOLOGY_CATALOG certification "
						+ "ON certification.CERTIFICATION_TECHNOLOGY_ID = profile.CERTIFICATION_TECHNOLOGY_ID "
						+ "WHERE certification.MASTER_TECHNOLOGY_ID = :key");
		case PROFESSIONAL_PROFILES -> List.of(
				"SELECT COUNT(*) FROM STUDENT_CERTIFICATION_PROFILE WHERE PROFESSIONAL_PROFILE_ID = :key");
		case TECHNOLOGICAL_PROFILES -> List.of(
				"SELECT COUNT(*) FROM STUDENT_CERTIFICATION_PROFILE WHERE TECHNOLOGICAL_PROFILE = "
						+ "(SELECT PROFILE_CODE FROM TECHNOLOGICAL_PROFILE_CATALOG "
						+ "WHERE TECHNOLOGICAL_PROFILE_ID = :key)",
				"SELECT COUNT(*) FROM CERTIFICATION_PROFILE_CATALOG WHERE SUGGESTED_TECH_PROFILE = "
						+ "(SELECT PROFILE_CODE FROM TECHNOLOGICAL_PROFILE_CATALOG "
						+ "WHERE TECHNOLOGICAL_PROFILE_ID = :key)");
		case QUESTION_TYPES -> List.of("SELECT COUNT(*) FROM QUESTION WHERE TYPE_CODE = :key");
		case DIFFICULTIES -> List.of("SELECT COUNT(*) FROM QUESTION WHERE DIFFICULTY_CODE = :key");
		};
	}
}
