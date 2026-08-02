package com.nexoskill.evaluation.students.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import com.nexoskill.evaluation.users.application.service.SecureTemporaryPasswordGenerator;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

class StudentServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-26T18:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 26);
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final TenantContext TENANT = TenantContext.organization(20L, "org-public", "ACME", false);

    private StudentRepository students;
    private StudentSessionRepository sessions;
    private OrganizationRepository organizations;
    private PasswordHasher passwords;
    private SecureTemporaryPasswordGenerator passwordGenerator;
    private OrganizationJpaEntity organization;
    private StudentService service;

    @BeforeEach
    void setUp() {
        students = mock(StudentRepository.class);
        sessions = mock(StudentSessionRepository.class);
        organizations = mock(OrganizationRepository.class);
        passwords = mock(PasswordHasher.class);
        passwordGenerator = mock(SecureTemporaryPasswordGenerator.class);
        when(passwordGenerator.generate()).thenReturn("Generated1!");
        service = new StudentService(students, sessions, organizations, passwords, new PasswordPolicy(),
                passwordGenerator, new AppProperties(), mock(AuditLogPort.class), CLOCK);
        organization = OrganizationJpaEntity.create("org-public", "ACME", "Acme",
                ContentMode.CLEAN, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), 1L, NOW);
        setId(organization, 20L);
        when(organizations.findById(20L)).thenReturn(Optional.of(organization));
        when(passwords.encode(any())).thenReturn("encoded-password");
        when(students.saveAndFlush(any(StudentJpaEntity.class))).thenAnswer(invocation -> {
            StudentJpaEntity saved = invocation.getArgument(0);
            if (saved.getId() == null) setId(saved, 30L);
            return saved;
        });
    }

    @Test
    void shouldReturnAnEmptyPageWhenTheOrganizationHasNoStudents() {
        when(students.existsByOrganizationId(20L)).thenReturn(false);
        StudentService.PageResult result = service.search(TENANT, null,
                com.nexoskill.evaluation.students.domain.StudentEffectiveStatus.ACTIVE, false, 0, 10);
        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
        verify(students, times(1)).existsByOrganizationId(20L);
        verify(students, never()).search(any(), any(), any(), any(), any());
    }

    @Test
    void shouldKeepTheResponseEmptyWhenTheRepositoryReturnsAnEmptyPage() {
        when(students.existsByOrganizationId(20L)).thenReturn(true);
        when(students.search(any(), any(), any(), any(), any())).thenReturn(Page.empty(PageRequest.of(0, 10)));
        StudentService.PageResult result = service.search(TENANT, null,
                com.nexoskill.evaluation.students.domain.StudentEffectiveStatus.ACTIVE, false, 0, 10);
        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }

    @Test
    void shouldCreateAnInactiveStudentWithoutCreatingASession() {
        StudentService.CreateResult result = service.create(TENANT,
                new StudentService.CreateCommand("ana@example.com", "", "", "Ana López",
                        StudentStatus.INACTIVE, TODAY, TODAY.plusDays(30)),
                new StudentService.Actor(1L, "127.0.0.1", "browser"));
        assertThat(result.temporaryPassword()).isEqualTo("Generated1!");
        assertThat(result.student().studentCode()).matches("AC\\d{2}30");
        assertThat(result.student().status()).isEqualTo(StudentStatus.INACTIVE);
        verify(passwords).encode("Generated1!");
        verify(sessions, never()).save(any(StudentSessionJpaEntity.class));
    }


    @Test
    void shouldNormalizeOrganizationPrefixWithoutAccents() {
        assertThat(StudentService.organizationPrefix("Ábaco")).isEqualTo("AB");
        assertThat(StudentService.organizationPrefix("BBVA")).isEqualTo("BB");
    }

    @Test
    void shouldRequireAStudentCodeWhenTheOrganizationUsesManualCodes() {
        organization.configureStudentCode(true, 1L, NOW);

        assertThatThrownBy(() -> service.create(TENANT,
                new StudentService.CreateCommand("ana@example.com", "", "", "Ana López",
                        StudentStatus.ACTIVE, TODAY, TODAY.plusDays(30), TODAY, null, null),
                new StudentService.Actor(1L, "127.0.0.1", "browser")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("STUDENT_CODE_REQUIRED"));
    }

    @Test
    void shouldRejectCorporateUserWithoutAdmissionDate() {
        assertThatThrownBy(() -> service.create(TENANT,
                new StudentService.CreateCommand("ana@example.com", "", "", "Ana López",
                        StudentStatus.INACTIVE, TODAY, TODAY.plusDays(30), null, null, "XMF7210"),
                new StudentService.Actor(1L, "127.0.0.1", "browser")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo("STUDENT_CORPORATE_USER_REQUIRES_ADMISSION_DATE"));
    }

    @Test
    void shouldPreserveCorporateUserWhenAdmissionDateIsRemoved() {
        StudentJpaEntity student = StudentJpaEntity.create("student-public", 20L, "AC1230",
                "ana@example.com", "ana@example.com", "password-hash", "Ana", "López", "Ana López",
                StudentStatus.ACTIVE, TODAY.minusDays(1), TODAY.plusDays(30), TODAY,
                "XMF7210", "XMF7210", NOW.plusSeconds(7200), 1L, NOW.minusSeconds(3600));
        setId(student, 30L);
        when(students.findByOrganizationIdAndPublicIdForUpdate(20L, "student-public"))
                .thenReturn(Optional.of(student));

        StudentService.StudentDetail updated = service.update(TENANT, "student-public",
                new StudentService.UpdateCommand("ana@example.com", "Ana", "López", "Ana López",
                        TODAY.minusDays(1), TODAY.plusDays(30), null, null, null, student.getVersion()),
                new StudentService.Actor(1L, "127.0.0.1", "browser"));

        assertThat(updated.status()).isEqualTo(StudentStatus.INACTIVE);
        assertThat(updated.corporateUser()).isEqualTo("XMF7210");
        verify(sessions).revokeActive(student.getId(), StudentSessionStatus.ACTIVE, StudentSessionStatus.REVOKED,
                StudentSessionRevocationReason.DEACTIVATED, NOW);
    }

    @Test
    void shouldRevokeTheActiveSessionWhenDeactivated() {
        StudentJpaEntity student = student(StudentStatus.ACTIVE, TODAY.plusDays(30));
        when(students.findByOrganizationIdAndPublicIdForUpdate(20L, "student-public")).thenReturn(Optional.of(student));
        when(students.save(student)).thenReturn(student);
        StudentService.StudentDetail updated = service.deactivate(TENANT, "student-public",
                new StudentService.Actor(1L, "127.0.0.1", "browser"));
        assertThat(updated.status()).isEqualTo(StudentStatus.INACTIVE);
        assertThat(student.getAdmissionDate()).isNull();
        verify(sessions).revokeActive(student.getId(), StudentSessionStatus.ACTIVE, StudentSessionStatus.REVOKED,
                StudentSessionRevocationReason.DEACTIVATED, NOW);
    }

    @Test
    void shouldRejectActivationWhenValidityAlreadyExpired() {
        StudentJpaEntity student = student(StudentStatus.INACTIVE, TODAY.minusDays(1));
        when(students.findByOrganizationIdAndPublicIdForUpdate(20L, "student-public")).thenReturn(Optional.of(student));
        assertThatThrownBy(() -> service.activate(TENANT, "student-public",
                new StudentService.Actor(1L, "127.0.0.1", "browser")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("STUDENT_ACCESS_DATES_INVALID"));
    }

    private StudentJpaEntity student(StudentStatus status, LocalDate expiresAt) {
        StudentJpaEntity student = StudentJpaEntity.create("student-public", 20L, "STU-001",
                "ana@example.com", "ana@example.com", "password-hash", "Ana", "López", "Ana López",
                status, TODAY.minusDays(1), expiresAt, NOW.plusSeconds(7200), 1L, NOW.minusSeconds(3600));
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
