package com.nexoskill.evaluation.globalcontent.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nexoskill.evaluation.globalcontent.domain.model.DistributionMode;
import com.nexoskill.evaluation.globalcontent.domain.model.GlobalContentType;
import com.nexoskill.evaluation.globalcontent.domain.model.GrantStatus;
import com.nexoskill.evaluation.globalcontent.infrastructure.persistence.ContentReplicationLinkRepository;
import com.nexoskill.evaluation.globalcontent.infrastructure.persistence.GlobalContentVersionRepository;
import com.nexoskill.evaluation.globalcontent.infrastructure.persistence.OrganizationGlobalContentGrantJpaEntity;
import com.nexoskill.evaluation.globalcontent.infrastructure.persistence.OrganizationGlobalContentGrantRepository;
import com.nexoskill.evaluation.organizations.domain.model.ContentMode;
import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationJpaEntity;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GlobalContentAccessPolicyTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-26T18:00:00Z"), ZoneOffset.UTC);

	@Test
	void anExplicitReferenceGrantAllowsCleanOrganizationsToReadGlobalContent() {
		OrganizationRepository organizations = mock(OrganizationRepository.class);
		OrganizationGlobalContentGrantRepository grants = mock(OrganizationGlobalContentGrantRepository.class);
		ContentReplicationLinkRepository links = mock(ContentReplicationLinkRepository.class);
		GlobalContentVersionRepository versions = mock(GlobalContentVersionRepository.class);
		GlobalContentAccessPolicy policy = new GlobalContentAccessPolicy(organizations, grants, links, versions, CLOCK);

		OrganizationJpaEntity organization = mock(OrganizationJpaEntity.class);
		OrganizationGlobalContentGrantJpaEntity grant = mock(OrganizationGlobalContentGrantJpaEntity.class);
		when(organization.isGlobal()).thenReturn(false);
		when(organization.getContentMode()).thenReturn(ContentMode.CLEAN);
		when(organizations.findById(21L)).thenReturn(Optional.of(organization));
		when(versions.existsByContentTypeAndContentId(GlobalContentType.QUESTION, 100L)).thenReturn(false);
		when(grant.getDistributionMode()).thenReturn(DistributionMode.GLOBAL_REFERENCE);
		when(grant.isOperational(CLOCK.instant())).thenReturn(true);
		when(grants.findAllByOrganizationIdAndContentTypeAndGlobalContentIdAndStatus(21L, GlobalContentType.QUESTION,
				100L, GrantStatus.ACTIVE)).thenReturn(List.of(grant));

		boolean allowed = policy.canRead(GlobalContentType.QUESTION, 100L, ContentScope.GLOBAL, 1L,
				TenantContext.organization(21L, "00000000-0000-0000-0000-000000000021", "ORG_21", false));

		assertThat(allowed).isTrue();
	}

	@Test
	void promotedDraftContentIsNotVisibleToCustomerOrganizations() {
		OrganizationRepository organizations = mock(OrganizationRepository.class);
		OrganizationGlobalContentGrantRepository grants = mock(OrganizationGlobalContentGrantRepository.class);
		ContentReplicationLinkRepository links = mock(ContentReplicationLinkRepository.class);
		GlobalContentVersionRepository versions = mock(GlobalContentVersionRepository.class);
		GlobalContentAccessPolicy policy = new GlobalContentAccessPolicy(organizations, grants, links, versions, CLOCK);

		OrganizationJpaEntity organization = mock(OrganizationJpaEntity.class);
		when(organization.isGlobal()).thenReturn(false);
		when(organization.getContentMode()).thenReturn(ContentMode.GLOBAL_CATALOG);
		when(organizations.findById(21L)).thenReturn(Optional.of(organization));
		when(versions.existsByContentTypeAndContentId(GlobalContentType.QUESTION, 100L)).thenReturn(true);
		when(versions.findFirstByContentTypeAndContentIdAndStatusOrderByVersionNumberDesc(GlobalContentType.QUESTION,
				100L, com.nexoskill.evaluation.globalcontent.domain.model.EditorialStatus.PUBLISHED))
				.thenReturn(Optional.empty());

		boolean allowed = policy.canRead(GlobalContentType.QUESTION, 100L, ContentScope.GLOBAL, 1L,
				TenantContext.organization(21L, "00000000-0000-0000-0000-000000000021", "ORG_21", false));

		assertThat(allowed).isFalse();
	}

	@Test
	void globalAdministratorCanReadOrganizationalContentFromGlobalContext() {
		OrganizationRepository organizations = mock(OrganizationRepository.class);
		OrganizationGlobalContentGrantRepository grants = mock(OrganizationGlobalContentGrantRepository.class);
		ContentReplicationLinkRepository links = mock(ContentReplicationLinkRepository.class);
		GlobalContentVersionRepository versions = mock(GlobalContentVersionRepository.class);
		GlobalContentAccessPolicy policy = new GlobalContentAccessPolicy(organizations, grants, links, versions, CLOCK);

		boolean allowed = policy.canRead(GlobalContentType.QUESTION, 100L, ContentScope.ORGANIZATION, 21L,
				TenantContext.global(1L, "00000000-0000-0000-0000-000000000001", "GLOBAL"));

		assertThat(allowed).isTrue();
	}

}
