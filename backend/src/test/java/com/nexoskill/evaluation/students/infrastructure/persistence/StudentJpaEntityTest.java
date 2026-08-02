package com.nexoskill.evaluation.students.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexoskill.evaluation.students.domain.StudentEffectiveStatus;
import com.nexoskill.evaluation.students.domain.StudentStatus;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class StudentJpaEntityTest {
    private static final Instant NOW = Instant.parse("2026-07-26T18:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 26);

    @Test
    void usesCalendarDatesAndFourFunctionalStatusesForAuthentication() {
        StudentJpaEntity future = student(StudentStatus.ACTIVE, TODAY.plusDays(1), TODAY.plusDays(30));
        StudentJpaEntity active = student(StudentStatus.ACTIVE, TODAY.minusDays(1), TODAY.plusDays(30));
        StudentJpaEntity expired = student(StudentStatus.ACTIVE, TODAY.minusDays(30), TODAY.minusDays(1));
        StudentJpaEntity inactiveExpired = student(StudentStatus.INACTIVE, TODAY.minusDays(30), TODAY.minusDays(1));

        assertThat(future.effectiveStatusOn(TODAY)).isEqualTo(StudentEffectiveStatus.ACTIVE);
        assertThat(future.canAuthenticateOn(TODAY, NOW)).isFalse();
        assertThat(active.canAuthenticateOn(TODAY, NOW)).isTrue();
        assertThat(expired.effectiveStatusOn(TODAY)).isEqualTo(StudentEffectiveStatus.EXPIRED);
        assertThat(expired.canAuthenticateOn(TODAY, NOW)).isFalse();
        assertThat(inactiveExpired.effectiveStatusOn(TODAY)).isEqualTo(StudentEffectiveStatus.INACTIVE);
    }

    @Test
    void shouldLockAfterTheConfiguredNumberOfFailedAttempts() {
        StudentJpaEntity student = student(StudentStatus.ACTIVE, TODAY.minusDays(1), TODAY.plusDays(30));
        student.registerFailedLogin(2, NOW, Duration.ofMinutes(15));
        student.registerFailedLogin(2, NOW.plusSeconds(1), Duration.ofMinutes(15));
        assertThat(student.getFailedLoginAttempts()).isZero();
        assertThat(student.getLockedUntil()).isEqualTo(NOW.plusSeconds(1).plus(Duration.ofMinutes(15)));
        assertThat(student.canAuthenticateOn(TODAY, NOW.plusSeconds(2))).isFalse();
    }

    @Test
    void softDeletesAndPreservesDeletionAuditMetadata() {
        StudentJpaEntity student = student(StudentStatus.ACTIVE, TODAY.minusDays(1), TODAY.plusDays(30));
        student.softDelete(9L, NOW, "ELIMINACIÓN LÓGICA DE PRUEBA");
        assertThat(student.getStatus()).isEqualTo(StudentStatus.DELETED);
        assertThat(student.getDeletedAt()).isEqualTo(NOW);
        assertThat(student.getDeletedBy()).isEqualTo(9L);
        assertThat(student.getDeletionReason()).isEqualTo("ELIMINACIÓN LÓGICA DE PRUEBA");
        assertThat(student.canAuthenticateOn(TODAY, NOW)).isFalse();
    }

    private StudentJpaEntity student(StudentStatus status, LocalDate validFrom, LocalDate expiresAt) {
        return StudentJpaEntity.create("student-public-id", 20L, "STU-001", "student@nexoskill.mx",
                "student@nexoskill.mx", "hash", "Ana", "López", "Ana López", status, validFrom, expiresAt,
                NOW.plus(Duration.ofDays(7)), 1L, NOW.minus(Duration.ofDays(1)));
    }
}
