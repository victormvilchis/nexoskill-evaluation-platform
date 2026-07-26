package com.nexoskill.evaluation.students.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexoskill.evaluation.students.domain.StudentEffectiveStatus;
import com.nexoskill.evaluation.students.domain.StudentStatus;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class StudentJpaEntityTest {
    private static final Instant NOW = Instant.parse("2026-07-26T18:00:00Z");

    @Test
    void shouldCalculatePendingActiveAndExpiredStatusFromValidity() {
        StudentJpaEntity pending = student(StudentStatus.ACTIVE, NOW.plusSeconds(60), NOW.plusSeconds(3600));
        StudentJpaEntity active = student(StudentStatus.ACTIVE, NOW.minusSeconds(60), NOW.plusSeconds(3600));
        StudentJpaEntity expired = student(StudentStatus.ACTIVE, NOW.minusSeconds(3600), NOW);

        assertThat(pending.effectiveStatusAt(NOW)).isEqualTo(StudentEffectiveStatus.PENDING);
        assertThat(active.effectiveStatusAt(NOW)).isEqualTo(StudentEffectiveStatus.ACTIVE);
        assertThat(expired.effectiveStatusAt(NOW)).isEqualTo(StudentEffectiveStatus.EXPIRED);
        assertThat(expired.canAuthenticateAt(NOW)).isFalse();
    }

    @Test
    void shouldLockAfterTheConfiguredNumberOfFailedAttempts() {
        StudentJpaEntity student = student(StudentStatus.ACTIVE, NOW.minusSeconds(60), NOW.plusSeconds(3600));

        student.registerFailedLogin(2, NOW, Duration.ofMinutes(15));
        student.registerFailedLogin(2, NOW.plusSeconds(1), Duration.ofMinutes(15));

        assertThat(student.getFailedLoginAttempts()).isZero();
        assertThat(student.getLockedUntil()).isEqualTo(NOW.plusSeconds(1).plus(Duration.ofMinutes(15)));
        assertThat(student.canAuthenticateAt(NOW.plusSeconds(2))).isFalse();
    }

    @Test
    void shouldRestoreADeletedStudentAsInactiveWithoutLosingIdentity() {
        StudentJpaEntity student = student(StudentStatus.ACTIVE, NOW.minusSeconds(60), NOW.plusSeconds(3600));
        student.softDelete(9L, "Baja solicitada", NOW);

        student.restore(10L, NOW.plusSeconds(60));

        assertThat(student.getStatus()).isEqualTo(StudentStatus.INACTIVE);
        assertThat(student.getDeletedAt()).isNull();
        assertThat(student.getDeletionReason()).isNull();
        assertThat(student.getStudentCode()).isEqualTo("STU-001");
    }

    private StudentJpaEntity student(StudentStatus status, Instant validFrom, Instant expiresAt) {
        return StudentJpaEntity.create("student-public-id", 20L, "STU-001", "student@nexoskill.mx",
                "student@nexoskill.mx", "hash", "Ana", "López", "Ana López", status,
                validFrom, expiresAt, NOW.plus(Duration.ofDays(7)), 1L, NOW.minus(Duration.ofDays(1)));
    }
}
