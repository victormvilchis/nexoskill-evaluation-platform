package com.nexoskill.evaluation.questionbank.application.service;

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

class QuestionCreationTargetResolverTest {
	private final OrganizationRepository organizations = mock(OrganizationRepository.class);
	private final Clock clock = Clock.fixed(Instant.parse("2026-07-29T12:00:00Z"), ZoneOffset.UTC);
	private final QuestionCreationTargetResolver resolver = new QuestionCreationTargetResolver(organizations, clock);

	@Test
	void administratorCanCreateGlobalContentEvenFromOrganizationalContext() {
		OrganizationJpaEntity global = mock(OrganizationJpaEntity.class);
		when(global.getId()).thenReturn(1L);
		when(global.getPublicId()).thenReturn("global-id");
		when(organizations.findByCode(OrganizationJpaEntity.GLOBAL_CODE)).thenReturn(Optional.of(global));

		var target = resolver.resolve(TenantContext.organization(20L, "org-id", "ORG", true), "GLOBAL", null);

		assertThat(target.scope()).isEqualTo(ContentScope.GLOBAL);
		assertThat(target.organizationId()).isEqualTo(1L);
	}

	@Test
	void organizationalUserCannotManipulateOwner() {
		assertThatThrownBy(() -> resolver.resolve(TenantContext.organization(20L, "org-id", "ORG", false),
				"ORGANIZATION", "other-org")).isInstanceOf(BusinessException.class)
				.hasMessageContaining("organización propietaria");
	}
}
