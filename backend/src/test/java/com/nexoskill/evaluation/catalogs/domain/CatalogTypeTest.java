package com.nexoskill.evaluation.catalogs.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class CatalogTypeTest {
    @Test
    void exposesTheCentralizedCatalogTypes() {
        assertThat(Arrays.stream(CatalogType.values()).map(Enum::name))
                .containsExactlyInAnyOrder(
                        "CATEGORIES", "TECHNOLOGIES", "PROFESSIONAL_PROFILES",
                        "TECHNOLOGICAL_PROFILES", "QUESTION_TYPES", "DIFFICULTIES");
    }

    @Test
    void marksOnlyCategoriesAsTenantAware() {
        assertThat(CatalogType.CATEGORIES.tenantAware()).isTrue();
        assertThat(Arrays.stream(CatalogType.values())
                .filter(type -> type != CatalogType.CATEGORIES))
                .allMatch(type -> !type.tenantAware());
    }
}
