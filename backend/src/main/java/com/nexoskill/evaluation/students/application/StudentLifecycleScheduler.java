package com.nexoskill.evaluation.students.application;

import com.nexoskill.evaluation.students.domain.StudentSessionRevocationReason;
import com.nexoskill.evaluation.students.domain.StudentSessionStatus;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentRepository;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentSessionRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class StudentLifecycleScheduler {
    private final StudentRepository studentRepository;
    private final StudentSessionRepository sessionRepository;
    private final Clock clock;

    public StudentLifecycleScheduler(StudentRepository studentRepository,
            StudentSessionRepository sessionRepository, Clock clock) {
        this.studentRepository = studentRepository;
        this.sessionRepository = sessionRepository;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.students.lifecycle-check-delay:PT5M}")
    @Transactional
    public void revokeInvalidSessions() {
        Instant now = clock.instant();
        sessionRepository.expireElapsedSessions(StudentSessionStatus.ACTIVE, StudentSessionStatus.EXPIRED,
                StudentSessionRevocationReason.EXPIRED, now);
        for (Long studentId : studentRepository.findExpiredActiveStudentIds(now)) {
            sessionRepository.revokeActive(studentId, StudentSessionStatus.ACTIVE, StudentSessionStatus.REVOKED,
                    StudentSessionRevocationReason.EXPIRED, now);
        }
    }
}
