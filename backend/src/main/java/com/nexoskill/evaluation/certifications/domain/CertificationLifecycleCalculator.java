package com.nexoskill.evaluation.certifications.domain;

import java.time.LocalDate;

/** Reglas puras de calendario para fechas límite, reintentos y vigencia. */
public final class CertificationLifecycleCalculator {
	private CertificationLifecycleCalculator() {
	}

	public static LocalDate deadline(LocalDate admissionDate, Integer months, Integer days) {
		return addPolicyPeriod(admissionDate, months, days);
	}

	public static LocalDate nextAttempt(LocalDate previousApplicationDate, Integer months, Integer days) {
		return addPolicyPeriod(previousApplicationDate, months, days);
	}

	public static LocalDate expiration(LocalDate applicationDate, Integer validityYears) {
		if (applicationDate == null)
			return null;
		int years = validityYears == null ? 2 : validityYears;
		if (years < 1)
			throw new IllegalArgumentException("La vigencia debe ser de al menos un año.");
		return applicationDate.plusYears(years);
	}

	public static CertificationValidityStatus validity(LocalDate expirationDate, LocalDate today,
			int expiringSoonDays) {
		if (expirationDate == null)
			return CertificationValidityStatus.NOT_OBTAINED;
		if (today.isAfter(expirationDate))
			return CertificationValidityStatus.EXPIRED;
		if (!today.plusDays(Math.max(expiringSoonDays, 0)).isBefore(expirationDate)) {
			return CertificationValidityStatus.EXPIRING_SOON;
		}
		return CertificationValidityStatus.VALID;
	}

	private static LocalDate addPolicyPeriod(LocalDate base, Integer months, Integer days) {
		if (base == null || (months == null && days == null))
			return null;
		int safeMonths = months == null ? 0 : months;
		int safeDays = days == null ? 0 : days;
		if (safeMonths < 0 || safeDays < 0) {
			throw new IllegalArgumentException("Los periodos de certificación no pueden ser negativos.");
		}
		return base.plusMonths(safeMonths).plusDays(safeDays);
	}
}
