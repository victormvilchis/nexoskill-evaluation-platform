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
import com.nexoskill.evaluation.authentication.application.port.out.SessionTokenGenerator;
import com.nexoskill.evaluation.authentication.application.port.out.TokenHasher;
import com.nexoskill.evaluation.organizations.domain.model.ContentMode;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationJpaEntity;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationRepository;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.shared.infrastructure.config.AppProperties;
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

class StudentAuthenticationServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-26T18:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private OrganizationRepository organizations;
    private StudentRepository students;
    private StudentSessionRepository sessions;
    private PasswordHasher passwords;
    private SessionTokenGenerator tokens;
    private TokenHasher tokenHasher;
    private AuditLogPort audit;
    private StudentAuthenticationService service;
    private OrganizationJpaEntity organization;

    @BeforeEach
    void setUp() {
        organizations = mock(OrganizationRepository.class);
        students = mock(StudentRepository.class);
        sessions = mock(StudentSessionRepository.class);
        passwords = mock(PasswordHasher.class);
        tokens = mock(SessionTokenGenerator.class);
        tokenHasher = mock(TokenHasher.class);
        audit = mock(AuditLogPort.class);
        service = new StudentAuthenticationService(organizations, students, sessions, passwords,
                new PasswordPolicy(), tokens, tokenHasher, new AppProperties(), audit, CLOCK);
        organization = OrganizationJpaEntity.create("org-public", "ACME", "Acme", ContentMode.CLEAN,
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), 1L, NOW);
        setId(organization, 20L);
        when(organizations.findByCode("ACME")).thenReturn(Optional.of(organization));
    }

    @Test
    void shouldBlockASecondActiveSession() {
        StudentJpaEntity student = student(StudentStatus.ACTIVE);
        StudentSessionJpaEntity activeSession = StudentSessionJpaEntity.create("session-1", 30L, 20L,
                "hash-existing", "127.0.0.1", "browser", NOW.minusSeconds(60), NOW.plusSeconds(3600));
        when(students.findForLogin(20L, "ANA@EXAMPLE.COM")).thenReturn(Optional.of(student));
        when(passwords.matches("Password1!", "password-hash")).thenReturn(true);
        when(sessions.findByStudentIdAndStatus(30L, StudentSessionStatus.ACTIVE))
                .thenReturn(Optional.of(activeSession));

        assertThatThrownBy(() -> service.login(command()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("STUDENT_SESSION_ALREADY_ACTIVE"));

        verify(tokens, never()).generate();
        verify(sessions, never()).save(any(StudentSessionJpaEntity.class));
    }

    @Test
    void shouldRejectAnInactiveStudentWithoutCreatingASession() {
        StudentJpaEntity student = student(StudentStatus.INACTIVE);
        when(students.findForLogin(20L, "ANA@EXAMPLE.COM")).thenReturn(Optional.of(student));
        when(passwords.matches("Password1!", "password-hash")).thenReturn(true);

        assertThatThrownBy(() -> service.login(command()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("STUDENT_ACCOUNT_UNAVAILABLE"));

        verify(sessions, never()).save(any(StudentSessionJpaEntity.class));
    }

    @Test
    void shouldCreateAnIndependentStudentSession() {
        StudentJpaEntity student = student(StudentStatus.ACTIVE);
        when(students.findForLogin(20L, "ANA@EXAMPLE.COM")).thenReturn(Optional.of(student));
        when(passwords.matches("Password1!", "password-hash")).thenReturn(true);
        when(sessions.findByStudentIdAndStatus(30L, StudentSessionStatus.ACTIVE)).thenReturn(Optional.empty());
        when(tokens.generate()).thenReturn("raw-student-token");
        when(tokenHasher.hash("raw-student-token")).thenReturn("student-token-hash");
        when(sessions.save(any(StudentSessionJpaEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StudentAuthenticationService.LoginResult result = service.login(command());

        assertThat(result.rawToken()).isEqualTo("raw-student-token");
        assertThat(result.student().publicId()).isEqualTo("student-public");
        assertThat(result.student().organizationCode()).isEqualTo("ACME");
        assertThat(result.expiresAt()).isEqualTo(NOW.plusSeconds(7200));
        verify(sessions).save(any(StudentSessionJpaEntity.class));
    }

    private StudentAuthenticationService.LoginCommand command() {
        return new StudentAuthenticationService.LoginCommand("acme", "Ana@Example.com", "Password1!",
                "127.0.0.1", "browser");
    }

    private StudentJpaEntity student(StudentStatus status) {
        StudentJpaEntity student = StudentJpaEntity.create("student-public", 20L, "STU-001",
                "ana@example.com", "ana@example.com", "password-hash", "Ana", "López", "Ana López",
                status, NOW.minusSeconds(3600), NOW.plusSeconds(7200), NOW.plusSeconds(7200), 1L, NOW);
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
