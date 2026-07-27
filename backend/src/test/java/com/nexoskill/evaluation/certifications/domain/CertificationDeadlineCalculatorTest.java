package com.nexoskill.evaluation.certifications.domain;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CertificationDeadlineCalculatorTest {
    @Test
    void developmentSecurityUsesCalendarMonthsAndFifteenDays() {
        assertThat(CertificationDeadlineCalculator.calculate(
                LocalDate.of(2026, 1, 31), 2, 15))
                .isEqualTo(LocalDate.of(2026, 4, 15));
    }

    @Test
    void normativeUsesTwoCalendarMonths() {
        assertThat(CertificationDeadlineCalculator.calculate(
                LocalDate.of(2026, 1, 31), 2, 0))
                .isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    void unconfiguredPolicyDoesNotInventADeadline() {
        assertThat(CertificationDeadlineCalculator.calculate(
                LocalDate.of(2026, 1, 10), null, null)).isNull();
    }
}
