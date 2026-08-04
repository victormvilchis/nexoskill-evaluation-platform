package com.nexoskill.evaluation.certifications.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CertificationLifecycleCalculatorTest {
	@Test
	void usesCalendarMonthsForInitialDeadlines() {
		LocalDate admission = LocalDate.of(2026, 1, 31);
		assertThat(CertificationLifecycleCalculator.deadline(admission, 1, 0)).isEqualTo(LocalDate.of(2026, 2, 28));
		assertThat(CertificationLifecycleCalculator.deadline(admission, 3, 0)).isEqualTo(LocalDate.of(2026, 4, 30));
	}

	@Test
	void usesMonthAndFifteenDaysForDevelopmentSecurityRetry() {
		assertThat(CertificationLifecycleCalculator.nextAttempt(LocalDate.of(2026, 1, 31), 1, 15))
				.isEqualTo(LocalDate.of(2026, 3, 15));
	}

	@Test
	void expirationUsesTwoCalendarYearsInsteadOfFixedDays() {
		assertThat(CertificationLifecycleCalculator.expiration(LocalDate.of(2024, 2, 29), 2))
				.isEqualTo(LocalDate.of(2026, 2, 28));
	}

	@Test
	void oneAndAgileCanRemainWithoutExpiration() {
		assertThat(CertificationLifecycleCalculator.validity(null, LocalDate.of(2026, 7, 29), 90))
				.isEqualTo(CertificationValidityStatus.NOT_OBTAINED);
	}
}
