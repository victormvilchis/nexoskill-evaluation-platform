package com.nexoskill.evaluation.students.application;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.students.domain.StudentSessionRevocationReason;
import com.nexoskill.evaluation.students.domain.StudentSessionStatus;
import com.nexoskill.evaluation.students.domain.StudentStatus;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentJpaEntity;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentRepository;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentSessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class StudentLifecycleScheduler {
	private final StudentRepository studentRepository;
	private final StudentSessionRepository sessionRepository;
	private final AuditLogPort audit;
	private final Clock clock;

	public StudentLifecycleScheduler(StudentRepository studentRepository, StudentSessionRepository sessionRepository,
			AuditLogPort audit, Clock clock) {
		this.studentRepository = studentRepository;
		this.sessionRepository = sessionRepository;
		this.audit = audit;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${app.students.lifecycle-check-delay:PT5M}")
	@Transactional
	public void revokeInvalidSessions() {
		Instant now = clock.instant();
		sessionRepository.expireElapsedSessions(StudentSessionStatus.ACTIVE, StudentSessionStatus.EXPIRED,
				StudentSessionRevocationReason.EXPIRED, now);
	}

}
