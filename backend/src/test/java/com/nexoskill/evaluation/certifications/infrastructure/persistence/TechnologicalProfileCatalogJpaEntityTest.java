package com.nexoskill.evaluation.certifications.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TechnologicalProfileCatalogJpaEntityTest {
    @Test
    void usesOnlyActiveAndInactiveOperationalStates() {
        Instant now = Instant.parse("2026-07-27T12:00:00Z");
        TechnologicalProfileCatalogJpaEntity profile = TechnologicalProfileCatalogJpaEntity.create(
                "00000000-0000-0000-4000-000000000001", "DEVELOPER", "Desarrollador",
                null, 10, ContentScope.GLOBAL, null, 1L, now);

        assertThat(profile.getStatus()).isEqualTo("ACTIVE");
        assertThat(profile.getContentScope()).isEqualTo(ContentScope.GLOBAL);
        assertThat(profile.getOwnerOrganizationId()).isNull();

        profile.changeStatus("INACTIVE", 2L, now.plusSeconds(60));

        assertThat(profile.getStatus()).isEqualTo("INACTIVE");
        assertThat(profile.getUpdatedBy()).isEqualTo(2L);
    }
}
