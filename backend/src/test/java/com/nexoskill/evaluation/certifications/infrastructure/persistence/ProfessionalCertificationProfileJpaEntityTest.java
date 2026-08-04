package com.nexoskill.evaluation.certifications.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ProfessionalCertificationProfileJpaEntityTest {
	@Test
	void preservesOrganizationalOwnership() {
		Instant now = Instant.parse("2026-07-29T18:00:00Z");
		ProfessionalCertificationProfileJpaEntity profile = ProfessionalCertificationProfileJpaEntity.create(
				"00000000-0000-0000-5000-000000000001", "JAVA_STD", "Java STD", "Perfil organizacional", 10,
				"DEVELOPER", ContentScope.ORGANIZATION, 25L, 8L, now);

		assertThat(profile.getStatus()).isEqualTo("ACTIVE");
		assertThat(profile.getContentScope()).isEqualTo(ContentScope.ORGANIZATION);
		assertThat(profile.getOwnerOrganizationId()).isEqualTo(25L);
		assertThat(profile.getSuggestedTechnologicalProfile()).isEqualTo("DEVELOPER");
	}
}
