package com.nexoskill.evaluation.certifications.application;

import com.nexoskill.evaluation.certifications.application.CertificationModels.Applicability;
import com.nexoskill.evaluation.certifications.application.CertificationModels.CycleView;
import com.nexoskill.evaluation.certifications.application.CertificationModels.Metrics;
import com.nexoskill.evaluation.certifications.domain.CertificationExamStatus;
import com.nexoskill.evaluation.certifications.domain.CertificationTrackingStatus;
import com.nexoskill.evaluation.certifications.domain.CertificationType;
import com.nexoskill.evaluation.certifications.domain.CertificationValidityStatus;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Consolida el estado vigente por área de certificación para evitar que varios
 * ciclos históricos inflen o contradigan los indicadores ejecutivos del
 * colaborador.
 */
final class CertificationSummaryCalculator {
	private CertificationSummaryCalculator() {
	}

	static Metrics calculate(Applicability applicability, List<CycleView> cycles) {
		List<CycleView> safeCycles = cycles == null ? List.of() : cycles;
		int applicableAreas = 0;
		int pending = 0;
		int scheduled = 0;
		int approved = 0;
		int notApproved = 0;
		int valid = 0;
		int expiringSoon = 0;
		int expired = 0;
		int pendingRecertifications = 0;

		for (CertificationType type : CertificationType.values()) {
			if (!applies(applicability, type))
				continue;
			applicableAreas++;

			List<CycleView> areaCycles = safeCycles.stream().filter(CycleView::active)
					.filter(cycle -> cycle.type() == type).toList();
			AreaState state = evaluate(type, areaCycles);

			if (state.pendingInitial())
				pending++;
			if (state.scheduled())
				scheduled++;
			if (state.approved())
				approved++;
			if (state.notApproved())
				notApproved++;
			if (state.valid())
				valid++;
			if (state.expiringSoon())
				expiringSoon++;
			if (state.expired())
				expired++;
			if (state.pendingRecertification())
				pendingRecertifications++;
		}

		return new Metrics(applicableAreas, pending, scheduled, approved, notApproved, valid, expiringSoon, expired,
				pendingRecertifications);
	}

	private static AreaState evaluate(CertificationType type, List<CycleView> cycles) {
		boolean nonExpiring = nonExpiring(type);
		List<CycleView> approvedCycles = cycles.stream().filter(CertificationSummaryCalculator::hasApprovedHistory)
				.toList();
		boolean approved = !approvedCycles.isEmpty();
		boolean scheduled = cycles.stream().anyMatch(CertificationSummaryCalculator::isScheduled);
		boolean notApproved = latestResultWasNotApproved(cycles);

		boolean expiringSoon = !nonExpiring && approvedCycles.stream()
				.anyMatch(cycle -> cycle.validityStatus() == CertificationValidityStatus.EXPIRING_SOON);
		boolean valid = nonExpiring ? approved
				: approvedCycles.stream().anyMatch(cycle -> cycle.validityStatus() == CertificationValidityStatus.VALID
						|| cycle.validityStatus() == CertificationValidityStatus.EXPIRING_SOON);
		boolean expired = !nonExpiring && approved && !valid && approvedCycles.stream()
				.anyMatch(cycle -> cycle.validityStatus() == CertificationValidityStatus.EXPIRED);
		boolean pendingInitial = !approved && !scheduled && !notApproved;

		return new AreaState(pendingInitial, scheduled, approved, notApproved, valid, expiringSoon, expired, expired);
	}

	private static boolean hasApprovedHistory(CycleView cycle) {
		return Boolean.TRUE.equals(cycle.approved()) || cycle.lastApprovedApplicationDate() != null
				|| cycle.trackingStatus() == CertificationTrackingStatus.APPROVED
				|| cycle.latestExamStatus() == CertificationExamStatus.PASSED;
	}

	private static boolean isScheduled(CycleView cycle) {
		return cycle.trackingStatus() == CertificationTrackingStatus.SCHEDULED
				|| cycle.latestExamStatus() == CertificationExamStatus.SCHEDULED
				|| cycle.latestExamStatus() == CertificationExamStatus.RESCHEDULED;
	}

	private static boolean latestResultWasNotApproved(List<CycleView> cycles) {
		return cycles.stream().filter(CertificationSummaryCalculator::hasResult)
				.max(Comparator.comparing(CertificationSummaryCalculator::resultDate)
						.thenComparing(cycle -> cycle.primary() ? 1 : 0))
				.map(cycle -> cycle.trackingStatus() == CertificationTrackingStatus.NOT_APPROVED
						|| cycle.latestExamStatus() == CertificationExamStatus.FAILED
						|| Boolean.FALSE.equals(cycle.approved()))
				.orElse(false);
	}

	private static boolean hasResult(CycleView cycle) {
		return cycle.applicationDate() != null || cycle.trackingStatus() == CertificationTrackingStatus.APPROVED
				|| cycle.trackingStatus() == CertificationTrackingStatus.NOT_APPROVED
				|| cycle.latestExamStatus() == CertificationExamStatus.PASSED
				|| cycle.latestExamStatus() == CertificationExamStatus.FAILED;
	}

	private static LocalDate resultDate(CycleView cycle) {
		if (cycle.applicationDate() != null)
			return cycle.applicationDate();
		if (cycle.lastApprovedApplicationDate() != null)
			return cycle.lastApprovedApplicationDate();
		return LocalDate.MIN;
	}

	private static boolean applies(Applicability applicability, CertificationType type) {
		if (applicability == null)
			return false;
		return switch (type) {
		case TECHNOLOGICAL -> applicability.technological();
		case DEVELOPMENT_SECURITY -> applicability.developmentSecurity();
		case NORMATIVE_TESTING -> applicability.normativeTesting();
		case ONE -> applicability.one();
		case AGILE -> applicability.agile();
		case JIRA -> applicability.jira();
		};
	}

	private static boolean nonExpiring(CertificationType type) {
		return type == CertificationType.ONE || type == CertificationType.AGILE || type == CertificationType.JIRA;
	}

	private record AreaState(boolean pendingInitial, boolean scheduled, boolean approved, boolean notApproved,
			boolean valid, boolean expiringSoon, boolean expired, boolean pendingRecertification) {
	}
}
