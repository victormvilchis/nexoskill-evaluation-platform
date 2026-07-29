package com.nexoskill.evaluation.students.application;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.students.domain.StudentSessionRevocationReason;
import com.nexoskill.evaluation.students.domain.StudentSessionStatus;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentJpaEntity;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentRepository;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentSessionRepository;
import java.time.Clock;
import java.time.Instant;
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
        for (Long studentId : studentRepository.findExpiredActiveStudentIds(now)) {
            StudentJpaEntity student = studentRepository.findByIdForUpdate(studentId).orElse(null);
            if (student == null || student.getStatus() != com.nexoskill.evaluation.students.domain.StudentStatus.ACTIVE
                    || student.getExpiresAt() == null || student.getExpiresAt().isAfter(now)) {
                continue;
            }
            student.expire(student.getUpdatedBy(), now);
            studentRepository.save(student);
            sessionRepository.revokeActive(studentId, StudentSessionStatus.ACTIVE, StudentSessionStatus.REVOKED,
                    StudentSessionRevocationReason.EXPIRED, now);
            audit.record(null, "STUDENT_EXPIRED", "STUDENTS",
                    "La vigencia del estudiante expiró y sus sesiones activas fueron revocadas.",
                    null, null, Map.of(
                            "studentPublicId", student.getPublicId(),
                            "organizationId", student.getOrganizationId()), now);
        }
    }
}
