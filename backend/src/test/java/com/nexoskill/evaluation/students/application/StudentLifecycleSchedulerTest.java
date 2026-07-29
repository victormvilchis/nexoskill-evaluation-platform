package com.nexoskill.evaluation.students.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.students.domain.StudentSessionRevocationReason;
import com.nexoskill.evaluation.students.domain.StudentSessionStatus;
import com.nexoskill.evaluation.students.domain.StudentStatus;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentJpaEntity;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentRepository;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentSessionRepository;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StudentLifecycleSchedulerTest {
    @Test
    void expiresStudentsAndRevokesTheirSessionsTransactionally() {
        Instant now = Instant.parse("2026-07-26T18:00:00Z");
        StudentRepository students = org.mockito.Mockito.mock(StudentRepository.class);
        StudentSessionRepository sessions = org.mockito.Mockito.mock(StudentSessionRepository.class);
        AuditLogPort audit = org.mockito.Mockito.mock(AuditLogPort.class);
        StudentJpaEntity first = student(11L, now.minusSeconds(1));
        StudentJpaEntity second = student(12L, now.minusSeconds(10));
        when(students.findExpiredActiveStudentIds(LocalDate.of(2026, 7, 26))).thenReturn(List.of(11L, 12L));
        when(students.findByIdForUpdate(11L)).thenReturn(Optional.of(first));
        when(students.findByIdForUpdate(12L)).thenReturn(Optional.of(second));
        StudentLifecycleScheduler scheduler = new StudentLifecycleScheduler(students, sessions, audit,
                Clock.fixed(now, ZoneOffset.UTC));

        scheduler.revokeInvalidSessions();

        verify(sessions).expireElapsedSessions(StudentSessionStatus.ACTIVE, StudentSessionStatus.EXPIRED,
                StudentSessionRevocationReason.EXPIRED, now);
        verify(sessions).revokeActive(11L, StudentSessionStatus.ACTIVE, StudentSessionStatus.REVOKED,
                StudentSessionRevocationReason.EXPIRED, now);
        verify(sessions).revokeActive(12L, StudentSessionStatus.ACTIVE, StudentSessionStatus.REVOKED,
                StudentSessionRevocationReason.EXPIRED, now);
        verify(students).save(first);
        verify(students).save(second);
        verify(audit, org.mockito.Mockito.times(2)).record(
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq("STUDENT_EXPIRED"),
                org.mockito.ArgumentMatchers.eq("STUDENTS"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.eq(now));
        assertThat(first.getStatus()).isEqualTo(StudentStatus.EXPIRED);
        assertThat(second.getStatus()).isEqualTo(StudentStatus.EXPIRED);
    }

    private StudentJpaEntity student(Long id, Instant expiresAt) {
        LocalDate expirationDate = LocalDate.ofInstant(expiresAt, ZoneOffset.UTC).minusDays(1);
        StudentJpaEntity student = StudentJpaEntity.create("student-" + id, 20L, "ST-" + id,
                "student" + id + "@example.com", "student" + id + "@example.com", "hash", "Nombre",
                "Apellidos", "Nombre Apellidos", StudentStatus.ACTIVE, expirationDate.minusDays(30), expirationDate,
                expiresAt.plusSeconds(3600), 1L, expiresAt.minusSeconds(3600));
        setId(student, id);
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
