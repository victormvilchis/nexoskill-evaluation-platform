package com.nexoskill.evaluation.forms.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationJpaEntity;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationRepository;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FormCreationTargetResolverTest {
	private final OrganizationRepository organizations = mock(OrganizationRepository.class);
	private final Clock clock = Clock.fixed(Instant.parse("2026-08-03T15:00:00Z"), ZoneOffset.UTC);
	private final FormCreationTargetResolver resolver = new FormCreationTargetResolver(organizations, clock);

	@Test
	void globalAdministratorCanCreateGlobalForm() {
		OrganizationJpaEntity global = mock(OrganizationJpaEntity.class);
		when(global.getId()).thenReturn(1L);
		when(global.getPublicId()).thenReturn("global-public-id");
		when(global.getCode()).thenReturn("GLOBAL");
		when(global.getName()).thenReturn("GLOBAL");
		when(organizations.findByCode(OrganizationJpaEntity.GLOBAL_CODE)).thenReturn(Optional.of(global));

		var target = resolver.resolve(TenantContext.organization(20L, "org-id", "ORG", true), "GLOBAL", null);

		assertThat(target.scope()).isEqualTo(ContentScope.GLOBAL);
		assertThat(target.organizationId()).isEqualTo(1L);
	}

	@Test
	void organizationalUserIsAlwaysRestrictedToOwnOrganization() {
		var target = resolver.resolve(TenantContext.organization(20L, "org-id", "ORG", false), "ORGANIZATION",
				"org-id");

		assertThat(target.scope()).isEqualTo(ContentScope.ORGANIZATION);
		assertThat(target.organizationId()).isEqualTo(20L);
		assertThat(target.organizationPublicId()).isEqualTo("org-id");
	}

	@Test
	void organizationalUserCannotManipulateOwnerOrGlobalScope() {
		TenantContext tenant = TenantContext.organization(20L, "org-id", "ORG", false);

		assertThatThrownBy(() -> resolver.resolve(tenant, "ORGANIZATION", "other-org"))
				.isInstanceOf(BusinessException.class).hasMessageContaining("organización propietaria");
		assertThatThrownBy(() -> resolver.resolve(tenant, "GLOBAL", null)).isInstanceOf(BusinessException.class)
				.hasMessageContaining("organización propietaria");
	}
}
