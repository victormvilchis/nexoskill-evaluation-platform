package com.nexoskill.evaluation.students.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.organizations.domain.model.OrganizationType;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationJpaEntity;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationRepository;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.students.domain.StudentEffectiveStatus;
import com.nexoskill.evaluation.students.domain.StudentStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class StudentFoundationServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-28T14:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 28);

    private StudentService studentService;
    private OrganizationRepository organizations;
    private StudentFoundationService service;
    private OrganizationJpaEntity organization;
    private TenantContext tenant;

    @BeforeEach
    void setUp() {
        studentService = mock(StudentService.class);
        organizations = mock(OrganizationRepository.class);
        service = new StudentFoundationService(studentService, organizations,
                mock(NamedParameterJdbcTemplate.class), mock(AuditLogPort.class), CLOCK);
        organization = mock(OrganizationJpaEntity.class);
        tenant = TenantContext.organization(21L, "00000000-0000-0000-0000-000000000021", "ORG_21", false);
        when(organizations.findById(21L)).thenReturn(Optional.of(organization));
        when(organization.getOrganizationType()).thenReturn(OrganizationType.CUSTOMER);
        when(organization.isGlobal()).thenReturn(false);
        when(organization.isOperational(any())).thenReturn(true);
        when(organization.getId()).thenReturn(21L);
        when(organization.getPublicId()).thenReturn(tenant.organizationPublicId());
        when(organization.getCode()).thenReturn(tenant.organizationCode());
        when(organization.getName()).thenReturn("Organización 21");
    }

    @Test
    void catalogsAreEmptyWhenOrganizationDoesNotApplyCertifications() {
        when(organization.isAppliesCertifications()).thenReturn(false);
        StudentFoundationService.CatalogBundle result = service.catalogs(tenant, null);
        assertThat(result.appliesCertifications()).isFalse();
        assertThat(result.organization().publicId()).isEqualTo(tenant.organizationPublicId());
        assertThat(result.profiles()).isEmpty();
        assertThat(result.technologicalProfiles()).isEmpty();
    }

    @Test
    void managerCannotQueryCatalogsFromAnotherOrganization() {
        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.catalogs(tenant, "00000000-0000-0000-0000-000000000022"));
        assertThat(exception.getCode()).isEqualTo("STUDENT_ORGANIZATION_FORBIDDEN");
    }

    @Test
    void globalAdministratorMustSelectOrganizationBeforeLoadingCatalogs() {
        TenantContext global = TenantContext.global(1L,
                "00000000-0000-0000-0000-000000000001", "GLOBAL");
        BusinessException exception = assertThrows(BusinessException.class, () -> service.catalogs(global, null));
        assertThat(exception.getCode()).isEqualTo("STUDENT_ORGANIZATION_REQUIRED");
    }

    @Test
    void rejectsCertificationDataWhenOrganizationDoesNotApplyCertifications() {
        when(organization.isAppliesCertifications()).thenReturn(false);
        StudentFoundationService.CreateCommand command = new StudentFoundationService.CreateCommand(
                null, "ST-99", "student@example.com", "Nombre", "Apellidos", null, StudentStatus.ACTIVE, TODAY, TODAY.plusDays(30), TODAY,
                "00000000-0000-0000-0000-000000000031", null,
                false, false, false, false, false);
        BusinessException exception = assertThrows(BusinessException.class, () -> service.create(tenant, command,
                new StudentService.Actor(7L, "127.0.0.1", "test")));
        assertThat(exception.getCode()).isEqualTo("STUDENT_CERTIFICATIONS_NOT_ENABLED");
        assertThat(exception.getFieldErrors()).containsKey("certifications");
    }

    @Test
    void managerCannotSendOrganizationEvenWhenItMatchesTheSession() {
        StudentFoundationService.CreateCommand command = new StudentFoundationService.CreateCommand(
                tenant.organizationPublicId(), "ST-99", "student@example.com", "Nombre", "Apellidos",
                null, StudentStatus.ACTIVE, TODAY, TODAY.plusDays(30), null,
                null, null, false, false, false, false, false);
        BusinessException exception = assertThrows(BusinessException.class, () -> service.create(tenant, command,
                new StudentService.Actor(7L, "127.0.0.1", "test")));
        assertThat(exception.getCode()).isEqualTo("STUDENT_ORGANIZATION_FORBIDDEN");
        assertThat(exception.getFieldErrors()).containsKey("organizationPublicId");
    }
}
