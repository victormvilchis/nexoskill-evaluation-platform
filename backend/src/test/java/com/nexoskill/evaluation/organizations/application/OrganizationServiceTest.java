package com.nexoskill.evaluation.organizations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexoskill.evaluation.organizations.domain.model.ContentMode;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationJpaEntity;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationLicensePolicyJpaEntity;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationLicensePolicyRepository;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationRepository;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class OrganizationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-26T16:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void shouldPersistTheCompleteLicensePolicyWhenCreatingAnOrganization() {
        OrganizationRepository organizations = mock(OrganizationRepository.class);
        OrganizationLicensePolicyRepository policies = mock(OrganizationLicensePolicyRepository.class);
        OrganizationService service = new OrganizationService(organizations, policies, CLOCK);
        when(organizations.existsByCode("ACME_MX")).thenReturn(false);
        when(organizations.saveAndFlush(any(OrganizationJpaEntity.class))).thenAnswer(invocation -> {
            OrganizationJpaEntity entity = invocation.getArgument(0);
            setId(entity, 41L);
            return entity;
        });
        when(policies.saveAndFlush(any(OrganizationLicensePolicyJpaEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrganizationService.OrganizationAggregate saved = service.create(new OrganizationService.CreateCommand(
                "Acme México", "acme mx", ContentMode.CUSTOM,
                LocalDate.of(2026, 8, 1), LocalDate.of(2027, 7, 31),
                125, 25, 5, 48, 10,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1)));

        assertThat(saved.organization().getCode()).isEqualTo("ACME_MX");
        assertThat(saved.organization().getName()).isEqualTo("Acme México");
        assertThat(saved.policy().getOrganizationId()).isEqualTo(41L);
        assertThat(saved.policy().getContractedSeats()).isEqualTo(125);
        assertThat(saved.policy().getIncludedReplacements()).isEqualTo(25);
        assertThat(saved.policy().getAdditionalReplacements()).isEqualTo(5);
        assertThat(saved.policy().getStandardReleaseHours()).isEqualTo(48);
        assertThat(saved.policy().getExhaustedReleaseDays()).isEqualTo(10);
        verify(organizations).saveAndFlush(any(OrganizationJpaEntity.class));
        verify(policies).saveAndFlush(any(OrganizationLicensePolicyJpaEntity.class));
    }

    @Test
    void shouldRejectDuplicatedOrganizationCodesBeforeWriting() {
        OrganizationRepository organizations = mock(OrganizationRepository.class);
        OrganizationLicensePolicyRepository policies = mock(OrganizationLicensePolicyRepository.class);
        OrganizationService service = new OrganizationService(organizations, policies, CLOCK);
        when(organizations.existsByCode("ACME_MX")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new OrganizationService.CreateCommand(
                "Acme México", "ACME_MX", ContentMode.CLEAN,
                null, null, 10, 2, 0, 24, 7,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Ya existe una organización con ese código.");

        verify(organizations, never()).saveAndFlush(any());
        verify(policies, never()).saveAndFlush(any());
    }

    @Test
    void shouldRejectAnInvalidReplacementCycleBeforeWriting() {
        OrganizationRepository organizations = mock(OrganizationRepository.class);
        OrganizationLicensePolicyRepository policies = mock(OrganizationLicensePolicyRepository.class);
        OrganizationService service = new OrganizationService(organizations, policies, CLOCK);
        when(organizations.existsByCode("ACME_MX")).thenReturn(false);

        assertThatThrownBy(() -> service.create(new OrganizationService.CreateCommand(
                "Acme México", "ACME_MX", ContentMode.CLEAN,
                null, null, 10, 2, 0, 24, 7,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("El periodo de sustituciones es inválido.");

        verify(organizations, never()).saveAndFlush(any());
        verify(policies, never()).saveAndFlush(any());
    }

    private static void setId(OrganizationJpaEntity entity, Long id) {
        try {
            Field field = OrganizationJpaEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
