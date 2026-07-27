package com.nexoskill.evaluation.certifications.domain;

import java.time.LocalDate;

public final class CertificationDeadlineCalculator {
    private CertificationDeadlineCalculator() {
    }

    public static LocalDate calculate(LocalDate enrollmentDate, Integer months, Integer days) {
        if (enrollmentDate == null || (months == null && days == null)) {
            return null;
        }
        int safeMonths = months == null ? 0 : months;
        int safeDays = days == null ? 0 : days;
        if (safeMonths < 0 || safeDays < 0) {
            throw new IllegalArgumentException("Los plazos de certificación no pueden ser negativos.");
        }
        return enrollmentDate.plusMonths(safeMonths).plusDays(safeDays);
    }
}
