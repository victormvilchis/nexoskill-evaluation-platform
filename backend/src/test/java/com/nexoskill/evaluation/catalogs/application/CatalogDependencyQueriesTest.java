package com.nexoskill.evaluation.catalogs.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexoskill.evaluation.catalogs.domain.CatalogType;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogDependencyQueriesTest {

    @Test
    void shouldCountEveryPersistedTechnologyRelationshipThatBlocksDeletion() {
        String sql = sqlFor(CatalogType.TECHNOLOGIES);

        assertThat(sql)
                .contains("QUESTION WHERE TECHNOLOGY_ID = :key")
                .contains("STUDENT WHERE TECHNOLOGY_ID = :key")
                .contains("STUDENT WHERE TALENT_TECHNOLOGY_ID = :key")
                .contains("STUDENT_CERTIFICATION_CYCLE WHERE TECHNOLOGY_ID = :key")
                .contains("certification.MASTER_TECHNOLOGY_ID = :key");
    }

    @Test
    void shouldDeleteEveryCertificationBridgeWithoutAssumingAUniqueMasterRelationship() {
        assertThat(CatalogDependencyQueries.deleteTechnologyCertificationLinksSql())
                .isEqualTo("DELETE FROM CERTIFICATION_TECHNOLOGY_CATALOG WHERE MASTER_TECHNOLOGY_ID = :key");
        assertThat(CatalogDependencyQueries.deleteTechnologySql())
                .contains("DELETE FROM QUESTION_TECHNOLOGY")
                .contains("TECHNOLOGY_ID = :key")
                .contains("VERSION_NO = :version");
    }

    @Test
    void shouldCountDirectAndCertificationProfessionalProfileRelationships() {
        String sql = sqlFor(CatalogType.PROFESSIONAL_PROFILES);

        assertThat(sql)
                .contains("STUDENT WHERE PROFESSIONAL_PROFILE_ID = :key")
                .contains("STUDENT_CERTIFICATION_PROFILE WHERE PROFESSIONAL_PROFILE_ID = :key");
    }

    @Test
    void shouldCountDirectAndCodeBasedTechnologicalProfileRelationships() {
        String sql = sqlFor(CatalogType.TECHNOLOGICAL_PROFILES);

        assertThat(sql)
                .contains("STUDENT WHERE TECHNOLOGICAL_PROFILE_ID = :key")
                .contains("STUDENT_CERTIFICATION_PROFILE WHERE TECHNOLOGICAL_PROFILE")
                .contains("CERTIFICATION_PROFILE_CATALOG WHERE SUGGESTED_TECH_PROFILE");
    }

    @Test
    void shouldCountEveryCategoryRelationshipThatBlocksDeletion() {
        String sql = sqlFor(CatalogType.CATEGORIES);

        assertThat(sql)
                .contains("QUESTION WHERE CATEGORY_ID = :key")
                .contains("QUESTION_CATEGORY_RELATION WHERE CATEGORY_ID = :key")
                .contains("COLLECTION_CATEGORY_RELATION WHERE CATEGORY_ID = :key")
                .contains("FORM_QUESTION_POOL WHERE CATEGORY_ID = :key")
                .contains("QUESTION_CATEGORY WHERE SOURCE_GLOBAL_ID = :key");
    }

    @Test
    void shouldProvideFunctionalLabelsForEveryDependencyQuery() {
        List<CatalogDependencyQueries.DependencyQuery> queries = CatalogDependencyQueries.forType(
                CatalogType.TECHNOLOGIES);

        assertThat(queries)
                .allSatisfy(query -> {
                    assertThat(query.label()).isNotBlank();
                    assertThat(query.sql()).contains(":key");
                });
    }

    private static String sqlFor(CatalogType type) {
        return CatalogDependencyQueries.forType(type).stream()
                .map(CatalogDependencyQueries.DependencyQuery::sql)
                .collect(java.util.stream.Collectors.joining(" "));
    }
}
