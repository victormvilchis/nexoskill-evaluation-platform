package com.nexoskill.evaluation.students.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.students.domain.StudentSessionRevocationReason;
import com.nexoskill.evaluation.students.domain.StudentSessionStatus;
import com.nexoskill.evaluation.students.domain.StudentStatus;
import com.nexoskill.evaluation.students.domain.TalentType;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentJpaEntity;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentRepository;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentSessionRepository;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StudentDeletionServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-29T16:00:00Z");
    private static final TenantContext TENANT = TenantContext.organization(20L, "org-public", "ORG", false);

    private StudentRepository students;
    private StudentSessionRepository sessions;
    private AuditLogPort audit;
    private StudentDeletionService service;

    @BeforeEach
    void setUp() {
        students = org.mockito.Mockito.mock(StudentRepository.class);
        sessions = org.mockito.Mockito.mock(StudentSessionRepository.class);
        audit = org.mockito.Mockito.mock(AuditLogPort.class);
        service = new StudentDeletionService(students, sessions, audit, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void requiresExplicitConfirmationBeforeLockingOrDeleting() {
        assertThatThrownBy(() -> service.deletePermanently(TENANT, "student-public", false,
                new StudentService.Actor(1L, "127.0.0.1", "test")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("STUDENT_DELETE_CONFIRMATION_REQUIRED");
                    assertThat(exception.getFieldErrors()).containsKey("confirmed");
                });

        verify(students, never()).findByOrganizationIdAndPublicIdForUpdate(any(), anyString());
        verify(students, never()).saveAndFlush(any());
        verify(sessions, never()).revokeActive(any(), any(), any(), any(), any());
    }

    @Test
    void softDeletesStudentAndPreservesHistoricalRelations() {
        StudentJpaEntity student = student();
        when(students.findByOrganizationIdAndPublicIdForUpdate(20L, "student-public"))
                .thenReturn(Optional.of(student));
        when(students.saveAndFlush(student)).thenReturn(student);

        StudentDeletionService.DeletionResult result = service.deletePermanently(TENANT, "student-public", true,
                new StudentService.Actor(1L, "127.0.0.1", "test"));

        assertThat(result.publicId()).isEqualTo("student-public");
        assertThat(result.operationReference()).isNotBlank();
        assertThat(result.deletedAt()).isEqualTo(NOW);
        assertThat(student.getStatus()).isEqualTo(StudentStatus.DELETED);
        assertThat(student.getDeletedAt()).isEqualTo(NOW);
        assertThat(student.getDeletedBy()).isEqualTo(1L);
        assertThat(student.getDeletionReason()).contains("ELIMINACIÓN LÓGICA");

        verify(students).saveAndFlush(student);
        verify(sessions).revokeActive(30L, StudentSessionStatus.ACTIVE, StudentSessionStatus.REVOKED,
                StudentSessionRevocationReason.DELETED, NOW);
        verify(audit).record(eq(1L), eq("STUDENT_DELETED"), eq("STUDENTS"),
                anyString(), eq("127.0.0.1"), eq("test"), any(), eq(NOW));
    }

    @Test
    void doesNotAllowTheCollaboratorEndpointToDeleteATalentBankRecord() {
        StudentJpaEntity talent = student();
        talent.moveToTalentBank(TalentType.BBVA_EXIT, 1L, NOW);
        when(students.findByOrganizationIdAndPublicIdForUpdate(20L, "student-public"))
                .thenReturn(Optional.of(talent));

        assertThatThrownBy(() -> service.deletePermanently(TENANT, "student-public", true,
                new StudentService.Actor(1L, "127.0.0.1", "test")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("STUDENT_NOT_FOUND"));

        verify(students, never()).saveAndFlush(any());
        verify(sessions, never()).revokeActive(any(), any(), any(), any(), any());
    }

    @Test
    void allowsGlobalAdministratorInSelectedCommercialOrganization() {
        TenantContext globalAdministratorInOrganization = TenantContext.organization(
                20L, "org-public", "ORG", true);
        StudentJpaEntity student = student();
        when(students.findByOrganizationIdAndPublicIdForUpdate(20L, "student-public"))
                .thenReturn(Optional.of(student));
        when(students.saveAndFlush(student)).thenReturn(student);

        StudentDeletionService.DeletionResult result = service.deletePermanently(globalAdministratorInOrganization,
                "student-public", true, new StudentService.Actor(1L, "127.0.0.1", "test"));

        assertThat(result.publicId()).isEqualTo("student-public");
        assertThat(student.getStatus()).isEqualTo(StudentStatus.DELETED);
        verify(students).findByOrganizationIdAndPublicIdForUpdate(20L, "student-public");
    }

    private StudentJpaEntity student() {
        StudentJpaEntity student = StudentJpaEntity.create("student-public", 20L, "ST-001",
                "student@example.com", "student@example.com", "hash", "Nombre", "Apellidos",
                "Nombre Apellidos", StudentStatus.ACTIVE, LocalDate.of(2026, 7, 28), LocalDate.of(2026, 8, 29),
                NOW.plusSeconds(7200), 1L, NOW.minusSeconds(3600));
        setId(student, 30L);
        return student;
    }

    private void setId(StudentJpaEntity student, Long id) {
        try {
            Field field = StudentJpaEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(student, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
