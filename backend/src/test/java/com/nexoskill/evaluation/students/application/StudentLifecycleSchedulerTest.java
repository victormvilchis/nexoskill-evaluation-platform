package com.nexoskill.evaluation.students.application;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.students.domain.StudentSessionRevocationReason;
import com.nexoskill.evaluation.students.domain.StudentSessionStatus;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentRepository;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentSessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class StudentLifecycleSchedulerTest {
	@Test
	void onlyExpiresElapsedSessionsBecauseOrganizationLicenseControlsAccessValidity() {
		Instant now = Instant.parse("2026-07-26T18:00:00Z");
		StudentRepository students = org.mockito.Mockito.mock(StudentRepository.class);
		StudentSessionRepository sessions = org.mockito.Mockito.mock(StudentSessionRepository.class);
		AuditLogPort audit = org.mockito.Mockito.mock(AuditLogPort.class);
		StudentLifecycleScheduler scheduler = new StudentLifecycleScheduler(students, sessions, audit,
				Clock.fixed(now, ZoneOffset.UTC));

		scheduler.revokeInvalidSessions();

		verify(sessions).expireElapsedSessions(StudentSessionStatus.ACTIVE, StudentSessionStatus.EXPIRED,
				StudentSessionRevocationReason.EXPIRED, now);
		verify(students, never()).findExpiredActiveStudentIds(org.mockito.ArgumentMatchers.any());
		verify(students, never()).save(org.mockito.ArgumentMatchers.any());
		verify(audit, never()).record(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.any());
	}
}
