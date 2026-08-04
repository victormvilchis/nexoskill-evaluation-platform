package com.nexoskill.evaluation.certifications.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexoskill.evaluation.certifications.application.CertificationModels.Applicability;
import com.nexoskill.evaluation.certifications.application.CertificationModels.CycleView;
import com.nexoskill.evaluation.certifications.application.CertificationModels.Metrics;
import com.nexoskill.evaluation.certifications.domain.CertificationExamStatus;
import com.nexoskill.evaluation.certifications.domain.CertificationLevel;
import com.nexoskill.evaluation.certifications.domain.CertificationProcessType;
import com.nexoskill.evaluation.certifications.domain.CertificationTrackingStatus;
import com.nexoskill.evaluation.certifications.domain.CertificationType;
import com.nexoskill.evaluation.certifications.domain.CertificationValidityStatus;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CertificationSummaryCalculatorTest {

	@Test
	void shouldKeepExpiringSoonAndNonExpiringApprovedAreasInsideValidTotal() {
		Applicability applicability = new Applicability(true, true, true, true, true, true);
		List<CycleView> cycles = List.of(approved(CertificationType.TECHNOLOGICAL, CertificationValidityStatus.VALID),
				approved(CertificationType.DEVELOPMENT_SECURITY, CertificationValidityStatus.EXPIRED),
				approved(CertificationType.NORMATIVE_TESTING, CertificationValidityStatus.EXPIRING_SOON),
				approved(CertificationType.ONE, CertificationValidityStatus.NOT_OBTAINED),
				approved(CertificationType.AGILE, CertificationValidityStatus.NOT_OBTAINED),
				approved(CertificationType.JIRA, CertificationValidityStatus.NOT_OBTAINED));

		Metrics metrics = CertificationSummaryCalculator.calculate(applicability, cycles);

		assertThat(metrics.applicableAreas()).isEqualTo(6);
		assertThat(metrics.approved()).isEqualTo(6);
		assertThat(metrics.pending()).isZero();
		assertThat(metrics.scheduled()).isZero();
		assertThat(metrics.notApproved()).isZero();
		assertThat(metrics.valid()).isEqualTo(5);
		assertThat(metrics.expiringSoon()).isEqualTo(1);
		assertThat(metrics.expired()).isEqualTo(1);
		assertThat(metrics.pendingRecertifications()).isEqualTo(1);
	}

	@Test
	void shouldNotTreatAnExpiredPreviouslyApprovedAreaAsInitialPending() {
		Applicability applicability = new Applicability(false, true, false, false, false, false);
		CycleView expired = approved(CertificationType.DEVELOPMENT_SECURITY, CertificationValidityStatus.EXPIRED);

		Metrics metrics = CertificationSummaryCalculator.calculate(applicability, List.of(expired));

		assertThat(metrics.approved()).isEqualTo(1);
		assertThat(metrics.pending()).isZero();
		assertThat(metrics.valid()).isZero();
		assertThat(metrics.expired()).isEqualTo(1);
		assertThat(metrics.pendingRecertifications()).isEqualTo(1);
	}

	@Test
	void shouldClassifyInitialPendingScheduledAndFailedAreasWithoutDoubleCountingThem() {
		Applicability applicability = new Applicability(true, true, true, false, false, false);
		CycleView scheduled = cycle(CertificationType.TECHNOLOGICAL, false, CertificationTrackingStatus.SCHEDULED,
				CertificationExamStatus.SCHEDULED, CertificationValidityStatus.NOT_OBTAINED, null, null, false);
		CycleView failed = cycle(CertificationType.DEVELOPMENT_SECURITY, false,
				CertificationTrackingStatus.NOT_APPROVED, CertificationExamStatus.FAILED,
				CertificationValidityStatus.NOT_OBTAINED, LocalDate.of(2026, 7, 15), null, false);

		Metrics metrics = CertificationSummaryCalculator.calculate(applicability, List.of(scheduled, failed));

		assertThat(metrics.applicableAreas()).isEqualTo(3);
		assertThat(metrics.pending()).isEqualTo(1);
		assertThat(metrics.scheduled()).isEqualTo(1);
		assertThat(metrics.notApproved()).isEqualTo(1);
		assertThat(metrics.approved()).isZero();
	}

	@Test
	void shouldCountAnAreaOnceWhenHistoricalAndCurrentCyclesCoexist() {
		Applicability applicability = new Applicability(false, true, false, false, false, false);
		CycleView historicalExpired = approved(CertificationType.DEVELOPMENT_SECURITY,
				CertificationValidityStatus.EXPIRED);
		CycleView pendingRecertification = cycle(CertificationType.DEVELOPMENT_SECURITY, true,
				CertificationTrackingStatus.SCHEDULED, CertificationExamStatus.SCHEDULED,
				CertificationValidityStatus.NOT_OBTAINED, null, null, false);

		Metrics metrics = CertificationSummaryCalculator.calculate(applicability,
				List.of(pendingRecertification, historicalExpired));

		assertThat(metrics.applicableAreas()).isEqualTo(1);
		assertThat(metrics.approved()).isEqualTo(1);
		assertThat(metrics.scheduled()).isEqualTo(1);
		assertThat(metrics.expired()).isEqualTo(1);
		assertThat(metrics.pendingRecertifications()).isEqualTo(1);
		assertThat(metrics.pending()).isZero();
	}

	private static CycleView approved(CertificationType type, CertificationValidityStatus validity) {
		LocalDate approvedDate = LocalDate.of(2025, 7, 11);
		boolean nonExpiring = type == CertificationType.ONE || type == CertificationType.AGILE
				|| type == CertificationType.JIRA;
		return cycle(type, false, CertificationTrackingStatus.APPROVED, CertificationExamStatus.PASSED, validity,
				nonExpiring ? null : approvedDate, nonExpiring ? null : approvedDate, true);
	}

	private static CycleView cycle(CertificationType type, boolean recertification,
			CertificationTrackingStatus trackingStatus, CertificationExamStatus examStatus,
			CertificationValidityStatus validityStatus, LocalDate applicationDate,
			LocalDate lastApprovedApplicationDate, boolean approved) {
		LocalDate expirationDate = validityStatus == CertificationValidityStatus.NOT_OBTAINED ? null
				: LocalDate.of(2026, 7, 11);
		return new CycleView(type.name() + "-" + trackingStatus.name(), type,
				type == CertificationType.TECHNOLOGICAL ? "technology-id" : null,
				type == CertificationType.TECHNOLOGICAL ? "Java" : null,
				type == CertificationType.TECHNOLOGICAL ? CertificationLevel.STD : null,
				type == CertificationType.TECHNOLOGICAL,
				recertification ? CertificationProcessType.RECERTIFICATION : CertificationProcessType.CERTIFICATION,
				trackingStatus, null,
				trackingStatus == CertificationTrackingStatus.SCHEDULED ? LocalDate.of(2026, 9, 1) : null,
				applicationDate, lastApprovedApplicationDate, approved, expirationDate, validityStatus, null, null,
				null, null, true, null, examStatus, 0, null, "MANUAL", 0L);
	}
}
