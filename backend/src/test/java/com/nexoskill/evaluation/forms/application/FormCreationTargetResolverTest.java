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
import java.util.List;
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

        var target = resolver.resolve(TenantContext.organization(20L, "org-id", "ORG", true),
                "GLOBAL", null);

        assertThat(target.scope()).isEqualTo(ContentScope.GLOBAL);
        assertThat(target.organizationId()).isEqualTo(1L);
    }

    @Test
    void organizationalUserIsAlwaysRestrictedToOwnOrganization() {
        OrganizationJpaEntity organization = enabledCustomer(20L, "org-id", "ORG", "Organization");
        when(organizations.findById(20L)).thenReturn(Optional.of(organization));

        var target = resolver.resolve(TenantContext.organization(20L, "org-id", "ORG", false),
                "ORGANIZATION", "org-id");

        assertThat(target.scope()).isEqualTo(ContentScope.ORGANIZATION);
        assertThat(target.organizationId()).isEqualTo(20L);
        assertThat(target.organizationPublicId()).isEqualTo("org-id");
    }

    @Test
    void organizationalUserCannotManipulateOwnerOrGlobalScope() {
        TenantContext tenant = TenantContext.organization(20L, "org-id", "ORG", false);

        assertThatThrownBy(() -> resolver.resolve(tenant, "ORGANIZATION", "other-org"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("organización propietaria");
        assertThatThrownBy(() -> resolver.resolve(tenant, "GLOBAL", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("organización propietaria");
    }
    @Test
    void globalAdministratorReceivesOnlyOperationalCustomerOrganizations() {
        OrganizationJpaEntity customer = mock(OrganizationJpaEntity.class);
        when(customer.getPublicId()).thenReturn("customer-public-id");
        when(customer.getCode()).thenReturn("CUSTOMER");
        when(customer.getName()).thenReturn("Customer");
        when(customer.isAppliesCertifications()).thenReturn(true);
        when(organizations.findOperationalByTypeAndStatus(
                com.nexoskill.evaluation.organizations.domain.model.OrganizationType.CUSTOMER,
                com.nexoskill.evaluation.organizations.domain.model.OrganizationStatus.ACTIVE,
                java.time.LocalDate.of(2026, 8, 3))).thenReturn(List.of(customer));

        var options = resolver.availableOrganizations(
                TenantContext.organization(1L, "global-public-id", "GLOBAL", true));

        assertThat(options).hasSize(1);
        assertThat(options.getFirst().publicId()).isEqualTo("customer-public-id");
    }

    @Test
    void acceptsLegacyActiveCustomerWithoutAStartDate() {
        OrganizationJpaEntity customer = mock(OrganizationJpaEntity.class);
        when(customer.getId()).thenReturn(20L);
        when(customer.getPublicId()).thenReturn("customer-public-id");
        when(customer.getCode()).thenReturn("CUSTOMER");
        when(customer.getName()).thenReturn("Customer");
        when(customer.getOrganizationType()).thenReturn(
                com.nexoskill.evaluation.organizations.domain.model.OrganizationType.CUSTOMER);
        when(customer.getStatus()).thenReturn(
                com.nexoskill.evaluation.organizations.domain.model.OrganizationStatus.ACTIVE);
        when(customer.getValidFrom()).thenReturn(null);
        when(customer.getExpiresOn()).thenReturn(null);
        when(customer.isAppliesCertifications()).thenReturn(true);
        when(organizations.findByPublicId("customer-public-id")).thenReturn(Optional.of(customer));

        var target = resolver.resolve(
                TenantContext.organization(1L, "global-public-id", "GLOBAL", true),
                "ORGANIZATION", "customer-public-id");

        assertThat(target.organizationId()).isEqualTo(20L);
        assertThat(target.organizationPublicId()).isEqualTo("customer-public-id");
    }


    @Test
    void excludesOrganizationsWithoutCertificationTracking() {
        OrganizationJpaEntity enabled = enabledCustomer(20L, "enabled-id", "ENABLED", "Enabled");
        OrganizationJpaEntity disabled = enabledCustomer(21L, "disabled-id", "DISABLED", "Disabled");
        when(disabled.isAppliesCertifications()).thenReturn(false);
        when(organizations.findOperationalByTypeAndStatus(
                com.nexoskill.evaluation.organizations.domain.model.OrganizationType.CUSTOMER,
                com.nexoskill.evaluation.organizations.domain.model.OrganizationStatus.ACTIVE,
                java.time.LocalDate.of(2026, 8, 3))).thenReturn(List.of(enabled, disabled));

        var options = resolver.availableOrganizations(
                TenantContext.organization(1L, "global-public-id", "GLOBAL", true));

        assertThat(options).extracting(FormModels.OrganizationOptionView::publicId)
                .containsExactly("enabled-id");
    }

    @Test
    void organizationalUserCannotUseFormsWhenCertificationTrackingIsDisabled() {
        OrganizationJpaEntity disabled = enabledCustomer(20L, "org-id", "ORG", "Organization");
        when(disabled.isAppliesCertifications()).thenReturn(false);
        when(organizations.findById(20L)).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> resolver.resolve(
                TenantContext.organization(20L, "org-id", "ORG", false),
                "ORGANIZATION", "org-id"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("seguimiento de certificaciones");
    }

    private OrganizationJpaEntity enabledCustomer(Long id, String publicId, String code, String name) {
        OrganizationJpaEntity customer = mock(OrganizationJpaEntity.class);
        when(customer.getId()).thenReturn(id);
        when(customer.getPublicId()).thenReturn(publicId);
        when(customer.getCode()).thenReturn(code);
        when(customer.getName()).thenReturn(name);
        when(customer.getOrganizationType()).thenReturn(
                com.nexoskill.evaluation.organizations.domain.model.OrganizationType.CUSTOMER);
        when(customer.getStatus()).thenReturn(
                com.nexoskill.evaluation.organizations.domain.model.OrganizationStatus.ACTIVE);
        when(customer.isAppliesCertifications()).thenReturn(true);
        return customer;
    }

}
