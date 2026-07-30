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
    void marksTheFourOrganizationalCatalogsAsTenantAware() {
        assertThat(Arrays.stream(CatalogType.values())
                .filter(CatalogType::tenantAware)
                .map(Enum::name))
                .containsExactlyInAnyOrder(
                        "CATEGORIES", "TECHNOLOGIES", "PROFESSIONAL_PROFILES",
                        "TECHNOLOGICAL_PROFILES");
        assertThat(CatalogType.QUESTION_TYPES.tenantAware()).isFalse();
        assertThat(CatalogType.DIFFICULTIES.tenantAware()).isFalse();
    }
}
