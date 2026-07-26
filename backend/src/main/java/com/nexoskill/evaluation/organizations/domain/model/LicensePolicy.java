package com.nexoskill.evaluation.organizations.domain.model;

import java.time.LocalDate;

public record LicensePolicy(
        int contractedSeats,
        int includedReplacements,
        int additionalReplacements,
        int standardReleaseHours,
        int exhaustedReplacementReleaseDays,
        LocalDate cycleStartsOn,
        LocalDate cycleEndsOn) {

    public LicensePolicy {
        if (contractedSeats < 0) throw new IllegalArgumentException("Los asientos contratados no pueden ser negativos.");
        if (includedReplacements < 0 || additionalReplacements < 0) {
            throw new IllegalArgumentException("Las sustituciones no pueden ser negativas.");
        }
        if (standardReleaseHours < 1) throw new IllegalArgumentException("La liberación estándar debe ser mayor a cero.");
        if (exhaustedReplacementReleaseDays < 0) {
            throw new IllegalArgumentException("El bloqueo antifraude no puede ser negativo.");
        }
        if (cycleStartsOn == null || cycleEndsOn == null || !cycleEndsOn.isAfter(cycleStartsOn)) {
            throw new IllegalArgumentException("El periodo de sustituciones es inválido.");
        }
    }

    public int totalReplacementCapacity() {
        return includedReplacements + additionalReplacements;
    }

    public static int recommendedIncludedReplacements(int seats) {
        if (seats <= 0) return 0;
        return Math.max(1, (int) Math.ceil(seats * 0.20d));
    }
}
