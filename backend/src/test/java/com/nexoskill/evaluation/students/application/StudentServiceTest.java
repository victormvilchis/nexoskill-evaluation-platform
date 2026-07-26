package com.nexoskill.evaluation.students.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.authentication.application.port.out.PasswordHasher;
import com.nexoskill.evaluation.organizations.domain.model.ContentMode;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationJpaEntity;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationRepository;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.shared.infrastructure.config.AppProperties;
import com.nexoskill.evaluation.students.domain.StudentSessionRevocationReason;
import com.nexoskill.evaluation.students.domain.StudentSessionStatus;
import com.nexoskill.evaluation.students.domain.StudentStatus;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentJpaEntity;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentRepository;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentSessionJpaEntity;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentSessionRepository;
import com.nexoskill.evaluation.users.application.service.PasswordPolicy;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StudentServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-26T18:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final TenantContext TENANT = TenantContext.organization(20L, "org-public", "ACME", true);

    private StudentRepository students;
    private StudentSessionRepository sessions;
    private OrganizationRepository organizations;
    private PasswordHasher passwords;
    private StudentService service;

    @BeforeEach
    void setUp() {
        students = mock(StudentRepository.class);
        sessions = mock(StudentSessionRepository.class);
        organizations = mock(OrganizationRepository.class);
        passwords = mock(PasswordHasher.class);
        AuditLogPort audit = mock(AuditLogPort.class);
        service = new StudentService(students, sessions, organizations, passwords, new PasswordPolicy(),
                new AppProperties(), audit, CLOCK);
        OrganizationJpaEntity organization = OrganizationJpaEntity.create("org-public", "ACME", "Acme",
                ContentMode.CLEAN, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), 1L, NOW);
        setId(organization, 20L);
        when(organizations.findById(20L)).thenReturn(Optional.of(organization));
        when(passwords.encode(any())).thenReturn("encoded-password");
        when(students.saveAndFlush(any(StudentJpaEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void shouldCreateAnInactiveStudentWithoutCreatingASession() {
        StudentService.StudentDetail created = service.create(TENANT,
                new StudentService.CreateCommand("stu-001", "ana@example.com", "Ana", "López", null,
                        "StrongPass1!", StudentStatus.INACTIVE, NOW, NOW.plusSeconds(3600)),
                new StudentService.Actor(1L, "127.0.0.1", "browser"));

        assertThat(created.studentCode()).isEqualTo("STU-001");
        assertThat(created.status()).isEqualTo(StudentStatus.INACTIVE);
        verify(sessions, never()).save(any(StudentSessionJpaEntity.class));
    }

    @Test
    void shouldRevokeTheActiveSessionWhenDeactivated() {
        StudentJpaEntity student = student(StudentStatus.ACTIVE, NOW.plusSeconds(3600));
        when(students.findByOrganizationIdAndPublicId(20L, "student-public")).thenReturn(Optional.of(student));
        when(students.save(student)).thenReturn(student);

        StudentService.StudentDetail updated = service.deactivate(TENANT, "student-public",
                new StudentService.Actor(1L, "127.0.0.1", "browser"));

        assertThat(updated.status()).isEqualTo(StudentStatus.INACTIVE);
        verify(sessions).revokeActive(student.getId(), StudentSessionStatus.ACTIVE, StudentSessionStatus.REVOKED,
                StudentSessionRevocationReason.DEACTIVATED, NOW);
    }

    @Test
    void shouldRejectActivationWhenValidityAlreadyExpired() {
        StudentJpaEntity student = student(StudentStatus.INACTIVE, NOW.minusSeconds(1));
        when(students.findByOrganizationIdAndPublicId(20L, "student-public")).thenReturn(Optional.of(student));

        assertThatThrownBy(() -> service.activate(TENANT, "student-public",
                new StudentService.Actor(1L, "127.0.0.1", "browser")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("STUDENT_EXPIRED"));
    }

    private StudentJpaEntity student(StudentStatus status, Instant expiresAt) {
        StudentJpaEntity student = StudentJpaEntity.create("student-public", 20L, "STU-001",
                "ana@example.com", "ana@example.com", "password-hash", "Ana", "López", "Ana López",
                status, NOW.minusSeconds(3600), expiresAt, NOW.plusSeconds(7200), 1L, NOW.minusSeconds(3600));
        setId(student, 30L);
        return student;
    }

    private static void setId(Object target, Long id) {
        try {
            Field field = target.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(target, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
