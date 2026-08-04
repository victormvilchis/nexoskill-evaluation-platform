package com.nexoskill.evaluation.certifications.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.nexoskill.evaluation.certifications.domain.*;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class StudentCertificationRequirementJpaEntityTest {
	@Test
	void rejectsScoresOutsideTheZeroToOneHundredScale() {
		StudentCertificationRequirementJpaEntity requirement = StudentCertificationRequirementJpaEntity.create(1L, 2L,
				CertificationType.DEVELOPMENT_SECURITY, LocalDate.of(2026, 4, 15), 3L,
				Instant.parse("2026-01-01T00:00:00Z"));

		assertThatThrownBy(() -> requirement.update(true, CertificationStatus.IN_PROGRESS,
				CertificationExamStatus.COMPLETED, LocalDate.of(2026, 4, 15), null, null, LocalDate.of(2026, 3, 10),
				new BigDecimal("101"), 1, null, null, 3L, Instant.parse("2026-03-10T00:00:00Z")))
				.isInstanceOf(BusinessException.class);
	}

	@Test
	void manualDeadlineRequiresAReason() {
		StudentCertificationRequirementJpaEntity requirement = StudentCertificationRequirementJpaEntity.create(1L, 2L,
				CertificationType.NORMATIVE_TESTING, LocalDate.of(2026, 3, 1), 3L,
				Instant.parse("2026-01-01T00:00:00Z"));

		assertThatThrownBy(() -> requirement.update(true, CertificationStatus.PENDING,
				CertificationExamStatus.NOT_SCHEDULED, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 15), " ", null,
				null, null, null, null, 3L, Instant.parse("2026-01-02T00:00:00Z")))
				.isInstanceOf(BusinessException.class);
	}
}
