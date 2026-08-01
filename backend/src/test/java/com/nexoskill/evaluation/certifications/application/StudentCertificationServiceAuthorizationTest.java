package com.nexoskill.evaluation.certifications.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.authentication.infrastructure.security.AuthenticatedUser;
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
    void administratorCannotOperateStudentCertifications() {
        AuditLogPort audit = mock(AuditLogPort.class);
        StudentCertificationService service = new StudentCertificationService(
                mock(StudentRepository.class), mock(OrganizationRepository.class),
                mock(NamedParameterJdbcTemplate.class), audit, Clock.fixed(NOW, ZoneOffset.UTC));
        AuthenticatedUser administrator = new AuthenticatedUser(1L, "admin-public", "admin@nexoskill.local",
                "Admin", "Global", "Admin Global", Set.of("ADMINISTRATOR"),
                Set.of("STUDENT_CERTIFICATION_MANAGE"), NOW, UserAccessStatus.ACTIVE,
                null, null, false, NOW, null);
        TenantContext tenant = TenantContext.global(1L, "global-public", "GLOBAL");

        assertThatThrownBy(() -> service.get(tenant, "student-public", administrator))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("CERTIFICATION_OPERATION_FORBIDDEN");
                    assertThat(exception.getMessage()).isEqualTo(
                            "La gestión operativa de certificaciones corresponde únicamente a Gestores y Supervisores de la organización.");
                });

        verify(audit).record(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq("STUDENT_CERTIFICATION_ACCESS_DENIED"),
                org.mockito.ArgumentMatchers.eq("STUDENT_CERTIFICATIONS"),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.eq(NOW));
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
        assertThat(StudentCertificationService.requiresApplicationDate(
                CertificationType.TECHNOLOGICAL, true)).isTrue();
        assertThat(StudentCertificationService.requiresApplicationDate(
                CertificationType.DEVELOPMENT_SECURITY, true)).isTrue();
        assertThat(StudentCertificationService.requiresApplicationDate(
                CertificationType.NORMATIVE_TESTING, true)).isTrue();
        assertThat(StudentCertificationService.requiresApplicationDate(
                CertificationType.ONE, true)).isFalse();
        assertThat(StudentCertificationService.requiresApplicationDate(
                CertificationType.AGILE, true)).isFalse();
        assertThat(StudentCertificationService.requiresApplicationDate(
                CertificationType.TECHNOLOGICAL, false)).isFalse();
        assertThat(StudentCertificationService.requiresApplicationDate(
                CertificationType.TECHNOLOGICAL, null)).isFalse();
    }
}
