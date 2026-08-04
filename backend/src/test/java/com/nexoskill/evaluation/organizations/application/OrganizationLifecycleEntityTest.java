package com.nexoskill.evaluation.organizations.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexoskill.evaluation.organizations.domain.model.ContentMode;
import com.nexoskill.evaluation.organizations.domain.model.OrganizationStatus;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationJpaEntity;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class OrganizationLifecycleEntityTest {

	@Test
	void softDeleteKeepsTheOrganizationAndRecordsLifecycleMetadata() {
		Instant createdAt = Instant.parse("2026-07-27T10:00:00Z");
		Instant deletedAt = Instant.parse("2026-07-27T11:00:00Z");
		OrganizationJpaEntity organization = OrganizationJpaEntity.createCustomer(
				"00000000-0000-0000-0000-000000000021", "ACME", "Acme", ContentMode.CLEAN, LocalDate.of(2026, 7, 27),
				null, 7L, createdAt);

		organization.transitionTo(OrganizationStatus.DELETED, "Baja administrativa", 9L, deletedAt);

		assertThat(organization.getStatus()).isEqualTo(OrganizationStatus.DELETED);
		assertThat(organization.getDeletedAt()).isEqualTo(deletedAt);
		assertThat(organization.getDeletedBy()).isEqualTo(9L);
		assertThat(organization.getStatusReason()).isEqualTo("Baja administrativa");
		assertThat(organization.getPublicId()).isEqualTo("00000000-0000-0000-0000-000000000021");
	}
}
