package com.nexoskill.evaluation.organizations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexoskill.evaluation.organizations.domain.model.ContentMode;
import com.nexoskill.evaluation.organizations.domain.model.OrganizationType;
import com.nexoskill.evaluation.organizations.domain.model.OrganizationStatus;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationJpaEntity;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationLicensePolicyJpaEntity;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationLicensePolicyRepository;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationRepository;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationSearchRow;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class OrganizationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-26T16:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void shouldExposeActiveInactiveAndExpiredStudentCountsPerOrganization() {
        OrganizationRepository organizations = mock(OrganizationRepository.class);
        OrganizationLicensePolicyRepository policies = mock(OrganizationLicensePolicyRepository.class);
        OrganizationSearchRow row = mock(OrganizationSearchRow.class);
        OrganizationJpaEntity organization = mock(OrganizationJpaEntity.class);
        when(row.getOrganization()).thenReturn(organization);
        when(row.getStudentCount()).thenReturn(14L);
        when(row.getActiveStudentCount()).thenReturn(10L);
        when(row.getInactiveStudentCount()).thenReturn(4L);
        when(row.getExpiredStudentCount()).thenReturn(3L);
        when(organizations.search(isNull(), eq(OrganizationStatus.ACTIVE),
                eq(LocalDate.of(2026, 7, 26)), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row)));

        OrganizationService service = new OrganizationService(organizations, policies, CLOCK);

        OrganizationService.OrganizationListItem result = service.search(
                null, OrganizationStatus.ACTIVE, 0, 10).getContent().getFirst();

        assertThat(result.studentCount()).isEqualTo(14);
        assertThat(result.activeStudentCount()).isEqualTo(10);
        assertThat(result.inactiveStudentCount()).isEqualTo(4);
        assertThat(result.expiredStudentCount()).isEqualTo(3);
    }

    @Test
    void shouldPersistTheCompleteLicensePolicyWhenCreatingAnOrganization() {
        OrganizationRepository organizations = mock(OrganizationRepository.class);
        OrganizationLicensePolicyRepository policies = mock(OrganizationLicensePolicyRepository.class);
        OrganizationService service = new OrganizationService(organizations, policies, CLOCK);
        when(organizations.existsByCode("ACME_MX")).thenReturn(false);
        when(organizations.saveAndFlush(any(OrganizationJpaEntity.class))).thenAnswer(invocation -> {
            OrganizationJpaEntity submitted = invocation.getArgument(0);
            assertThat(submitted.getId()).isNull();
            assertThat(submitted.getVersion()).isNull();

            // Simula el comportamiento de EntityManager.merge: el repositorio devuelve
            // una instancia administrada distinta y la original conserva id nulo.
            OrganizationJpaEntity persisted = OrganizationJpaEntity.createCustomer(
                    submitted.getPublicId(), submitted.getCode(), submitted.getName(),
                    submitted.getContentMode(), submitted.getValidFrom(), submitted.getExpiresOn(),
                    null, NOW);
            setId(persisted, 41L);
            setVersion(persisted, 0L);
            return persisted;
        });
        when(policies.saveAndFlush(any(OrganizationLicensePolicyJpaEntity.class)))
                .thenAnswer(invocation -> {
                    OrganizationLicensePolicyJpaEntity persisted = invocation.getArgument(0);
                    setField(persisted, "id", 51L);
                    setField(persisted, "version", 0L);
                    return persisted;
                });

        OrganizationService.OrganizationAggregate saved = service.create(new OrganizationService.CreateCommand(
                "Acme México", "acme mx", ContentMode.CUSTOM,
                LocalDate.of(2026, 8, 1), LocalDate.of(2027, 7, 31),
                125, 25, 5, 48, 10,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1)));

        assertThat(saved.organization().getCode()).isEqualTo("ACME_MX");
        assertThat(saved.organization().getName()).isEqualTo("Acme México");
        assertThat(saved.organization().getOrganizationType()).isEqualTo(OrganizationType.CUSTOMER);
        assertThat(saved.organization().getValidFrom()).isEqualTo(LocalDate.of(2026, 7, 26));
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
    void shouldRejectLicenseCreationWhenRepositoryDoesNotReturnTheGeneratedOrganizationId() {
        OrganizationRepository organizations = mock(OrganizationRepository.class);
        OrganizationLicensePolicyRepository policies = mock(OrganizationLicensePolicyRepository.class);
        OrganizationService service = new OrganizationService(organizations, policies, CLOCK);
        when(organizations.existsByCode("ACME_MX")).thenReturn(false);
        when(organizations.saveAndFlush(any(OrganizationJpaEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service.create(new OrganizationService.CreateCommand(
                "Acme México", "ACME_MX", ContentMode.CLEAN,
                null, 10, 2, 0, 24, 7,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("No fue posible crear la organización debido a una inconsistencia de persistencia.");

        verify(policies, never()).saveAndFlush(any());
    }

    @Test
    void shouldCreateNewOrganizationAndPolicyWithNullJpaVersion() {
        OrganizationJpaEntity organization = OrganizationJpaEntity.createCustomer(
                "00000000-0000-0000-0000-000000000099", "ACME_MX", "Acme México",
                ContentMode.CLEAN, LocalDate.of(2026, 7, 26), null, null, NOW);
        OrganizationLicensePolicyJpaEntity policy = OrganizationLicensePolicyJpaEntity.create(
                99L, 10, 2, 0, 24, 7,
                LocalDate.of(2026, 7, 26), LocalDate.of(2026, 8, 26), NOW);

        assertThat(organization.getVersion()).isNull();
        assertThat(policy.getVersion()).isNull();
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
                .hasMessage("Ya existe una organización con el mismo nombre o código.");

        verify(organizations, never()).saveAndFlush(any());
        verify(policies, never()).saveAndFlush(any());
    }

    @Test
    void shouldRejectTheReservedGlobalCodeBeforeWriting() {
        OrganizationRepository organizations = mock(OrganizationRepository.class);
        OrganizationLicensePolicyRepository policies = mock(OrganizationLicensePolicyRepository.class);
        OrganizationService service = new OrganizationService(organizations, policies, CLOCK);

        assertThatThrownBy(() -> service.create(new OrganizationService.CreateCommand(
                "Global comercial", "GLOBAL", ContentMode.CLEAN,
                null, 10, 2, 0, 24, 7,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("El código GLOBAL está reservado por el sistema.");

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
        setField(entity, "id", id);
    }

    private static void setVersion(OrganizationJpaEntity entity, Long version) {
        setField(entity, "version", version);
    }

    private static void setField(Object entity, String fieldName, Long value) {
        try {
            Field field = entity.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(entity, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
