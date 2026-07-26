package com.nexoskill.evaluation.students.application;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexoskill.evaluation.students.domain.StudentSessionRevocationReason;
import com.nexoskill.evaluation.students.domain.StudentSessionStatus;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentRepository;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentSessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class StudentLifecycleSchedulerTest {
    @Test
    void shouldExpireElapsedSessionsAndRevokeSessionsOfExpiredStudents() {
        Instant now = Instant.parse("2026-07-26T18:00:00Z");
        StudentRepository students = Mockito.mock(StudentRepository.class);
        StudentSessionRepository sessions = Mockito.mock(StudentSessionRepository.class);
        when(students.findExpiredActiveStudentIds(now)).thenReturn(List.of(11L, 12L));
        StudentLifecycleScheduler scheduler = new StudentLifecycleScheduler(students, sessions,
                Clock.fixed(now, ZoneOffset.UTC));

        scheduler.revokeInvalidSessions();

        verify(sessions).expireElapsedSessions(StudentSessionStatus.ACTIVE, StudentSessionStatus.EXPIRED,
                StudentSessionRevocationReason.EXPIRED, now);
        verify(sessions).revokeActive(11L, StudentSessionStatus.ACTIVE, StudentSessionStatus.REVOKED,
                StudentSessionRevocationReason.EXPIRED, now);
        verify(sessions).revokeActive(12L, StudentSessionStatus.ACTIVE, StudentSessionStatus.REVOKED,
                StudentSessionRevocationReason.EXPIRED, now);
    }
}
