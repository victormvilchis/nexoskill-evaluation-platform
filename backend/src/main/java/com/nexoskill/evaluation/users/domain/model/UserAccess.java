package com.nexoskill.evaluation.users.domain.model;

import java.time.Instant;

public record UserAccess(
        Instant startsAt,
        Instant expiresAt,
        UserAccessStatus status
) {
    public UserAccess {
        if (startsAt == null) {
            throw new IllegalArgumentException("La fecha de inicio es obligatoria");
        }
        if (status == null) {
            throw new IllegalArgumentException("El estado de acceso es obligatorio");
        }
        if (expiresAt != null && !expiresAt.isAfter(startsAt)) {
            throw new IllegalArgumentException(
                    "La fecha de vencimiento debe ser posterior al inicio"
            );
        }
    }

    public boolean isActiveAt(Instant now) {
        if (status != UserAccessStatus.ACTIVE || now.isBefore(startsAt)) {
            return false;
        }
        return expiresAt == null || now.isBefore(expiresAt);
    }
}
