package com.nexoskill.evaluation.certifications.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.authentication.infrastructure.security.AuthenticatedUser;
import com.nexoskill.evaluation.certifications.domain.CertificationExamStatus;
import com.nexoskill.evaluation.certifications.domain.CertificationTrackingStatus;
import com.nexoskill.evaluation.certifications.domain.CertificationType;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationRepository;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentRepository;
import com.nexoskill.evaluation.users.domain.model.UserAccessStatus;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class StudentCertificationServiceAuthorizationTest {
	private static final Instant NOW = Instant.parse("2026-07-29T20:00:00Z");

	@Test
	void administratorCanResolveStudentScopeFromGlobalContext() {
		AuditLogPort audit = mock(AuditLogPort.class);
		StudentRepository students = mock(StudentRepository.class);
		when(students.findByPublicId("student-public")).thenReturn(java.util.Optional.empty());
		StudentCertificationService service = new StudentCertificationService(students,
				mock(OrganizationRepository.class), mock(NamedParameterJdbcTemplate.class), audit,
				Clock.fixed(NOW, ZoneOffset.UTC));
		AuthenticatedUser administrator = new AuthenticatedUser(1L, "admin-public", "admin@nexoskill.local", "Admin",
				"Global", "Admin Global", Set.of("ADMINISTRATOR"), Set.of("STUDENT_CERTIFICATION_MANAGE"), NOW,
				UserAccessStatus.ACTIVE, null, null, false, NOW, null);
		TenantContext tenant = TenantContext.global(1L, "global-public", "GLOBAL");

		assertThatThrownBy(() -> service.get(tenant, "student-public", administrator)).isInstanceOfSatisfying(
				BusinessException.class, exception -> assertThat(exception.getCode()).isEqualTo("STUDENT_NOT_FOUND"));

		verify(students).findByPublicId("student-public");
	}

	@Test
	void importPermissionAuthorizesCertificationSnapshotsWithoutManualManagementPermission() {
		AuditLogPort audit = mock(AuditLogPort.class);
		StudentRepository students = mock(StudentRepository.class);
		when(students.findByOrganizationIdAndPublicId(10L, "student-public"))
				.thenReturn(java.util.Optional.empty());
		StudentCertificationService service = new StudentCertificationService(students,
				mock(OrganizationRepository.class), mock(NamedParameterJdbcTemplate.class), audit,
				Clock.fixed(NOW, ZoneOffset.UTC));
		AuthenticatedUser importer = new AuthenticatedUser(2L, "importer-public", "importer@nexoskill.local",
				"Service", "Manager", "Service Manager", Set.of("CUSTOM_SERVICE_MANAGER"),
				Set.of("STUDENT_IMPORT"), NOW, UserAccessStatus.ACTIVE, null, null, false, NOW, null);
		TenantContext tenant = TenantContext.organization(10L, "organization-public", "ORG", false);

		assertThatThrownBy(() -> service.importSnapshot(tenant, "student-public", null, importer))
				.isInstanceOfSatisfying(BusinessException.class,
						exception -> assertThat(exception.getCode()).isEqualTo("STUDENT_NOT_FOUND"));

		verify(students).findByOrganizationIdAndPublicId(10L, "student-public");
	}

	@Test
	void manualCertificationPermissionDoesNotAuthorizeImportSnapshots() {
		AuditLogPort audit = mock(AuditLogPort.class);
		StudentCertificationService service = new StudentCertificationService(mock(StudentRepository.class),
				mock(OrganizationRepository.class), mock(NamedParameterJdbcTemplate.class), audit,
				Clock.fixed(NOW, ZoneOffset.UTC));
		AuthenticatedUser manager = new AuthenticatedUser(3L, "manager-public", "manager@nexoskill.local",
				"Certification", "Manager", "Certification Manager", Set.of("CUSTOM_CERT_MANAGER"),
				Set.of("STUDENT_VIEW", "STUDENT_CERTIFICATION_MANAGE"), NOW, UserAccessStatus.ACTIVE,
				null, null, false, NOW, null);
		TenantContext tenant = TenantContext.organization(10L, "organization-public", "ORG", false);

		assertThatThrownBy(() -> service.importSnapshot(tenant, "student-public", null, manager))
				.isInstanceOfSatisfying(BusinessException.class, exception -> {
					assertThat(exception.getCode()).isEqualTo("CERTIFICATION_OPERATION_FORBIDDEN");
					assertThat(exception.getMessage()).isEqualTo("No tienes permiso para importar colaboradores.");
				});
	}

	@Test
	void bindsNullableCertificationDatesAsOracleDates() {
		MapSqlParameterSource parameters = new MapSqlParameterSource();
		StudentCertificationService.addDateParameter(parameters, "applicationDate", null);
		assertThat(parameters.getValue("applicationDate")).isNull();
		assertThat(parameters.getSqlType("applicationDate")).isEqualTo(Types.DATE);

		LocalDate deadline = LocalDate.of(2025, 12, 15);
		StudentCertificationService.addDateParameter(parameters, "deadlineDate", deadline);
		assertThat(parameters.getValue("deadlineDate")).isEqualTo(java.sql.Date.valueOf(deadline));
		assertThat(parameters.getSqlType("deadlineDate")).isEqualTo(Types.DATE);
	}

	@Test
	void requiresApplicationDateOnlyForExamManagedCertifications() {
		assertThat(StudentCertificationService.requiresApplicationDate(CertificationType.TECHNOLOGICAL, true)).isTrue();
		assertThat(StudentCertificationService.requiresApplicationDate(CertificationType.DEVELOPMENT_SECURITY, true))
				.isTrue();
		assertThat(StudentCertificationService.requiresApplicationDate(CertificationType.NORMATIVE_TESTING, true))
				.isTrue();
		assertThat(StudentCertificationService.requiresApplicationDate(CertificationType.ONE, true)).isFalse();
		assertThat(StudentCertificationService.requiresApplicationDate(CertificationType.AGILE, true)).isFalse();
		assertThat(StudentCertificationService.requiresApplicationDate(CertificationType.JIRA, true)).isFalse();
		assertThat(StudentCertificationService.requiresApplicationDate(CertificationType.TECHNOLOGICAL, false))
				.isFalse();
		assertThat(StudentCertificationService.requiresApplicationDate(CertificationType.TECHNOLOGICAL, null))
				.isFalse();
	}

	@Test
	void summarizesManualResultsWithoutInventingExamDataForNonExpiringCertifications() {
		CertificationModels.CycleCommand approved = new CertificationModels.CycleCommand(null,
				CertificationType.TECHNOLOGICAL, null, null, true, CertificationTrackingStatus.APPROVED, null,
				LocalDate.of(2025, 11, 26), true, null, null, null, true, null, java.util.List.of());
		CertificationModels.CycleCommand failed = new CertificationModels.CycleCommand(null,
				CertificationType.DEVELOPMENT_SECURITY, null, null, false, CertificationTrackingStatus.NOT_APPROVED,
				null, LocalDate.of(2026, 5, 18), false, null, null, null, true, null, java.util.List.of());
		CertificationModels.CycleCommand nonExpiring = new CertificationModels.CycleCommand(null,
				CertificationType.JIRA, null, null, false, CertificationTrackingStatus.APPROVED, null, null, true, null,
				null, null, true, null, java.util.List.of());

		assertThat(StudentCertificationService.manualExamStatus(CertificationType.TECHNOLOGICAL, approved))
				.isEqualTo(CertificationExamStatus.PASSED);
		assertThat(StudentCertificationService.manualExamStatus(CertificationType.DEVELOPMENT_SECURITY, failed))
				.isEqualTo(CertificationExamStatus.FAILED);
		assertThat(StudentCertificationService.manualExamStatus(CertificationType.JIRA, nonExpiring))
				.isEqualTo(CertificationExamStatus.NOT_SCHEDULED);
	}

}
