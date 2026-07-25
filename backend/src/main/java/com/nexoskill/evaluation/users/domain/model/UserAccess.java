package com.nexoskill.evaluation.users.domain.model;

import java.time.Instant;

public record UserAccess(Instant startsAt, Instant expiresAt, UserAccessStatus status) {
	public UserAccess {
		if (startsAt == null) {
			throw new IllegalArgumentException("La fecha de inicio es obligatoria");
		}
		if (status == null) {
			throw new IllegalArgumentException("El estado de acceso es obligatorio");
		}
		if (expiresAt != null && !expiresAt.isAfter(startsAt)) {
			throw new IllegalArgumentException("La fecha de vencimiento debe ser posterior al inicio");
		}
	}

	public UserAccessStatus effectiveStatusAt(Instant now) {
		if (status == UserAccessStatus.SUSPENDED || status == UserAccessStatus.CANCELED
				|| status == UserAccessStatus.EXPIRED) {
			return status;
		}
		if (now.isBefore(startsAt)) {
			return UserAccessStatus.PENDING;
		}
		if (expiresAt != null && !now.isBefore(expiresAt)) {
			return UserAccessStatus.EXPIRED;
		}
		return status;
	}

	public boolean isActiveAt(Instant now) {
		return effectiveStatusAt(now) == UserAccessStatus.ACTIVE;
	}

	public Instant capSessionExpiration(Instant requestedExpiration) {
		if (expiresAt == null || expiresAt.isAfter(requestedExpiration)) {
			return requestedExpiration;
		}
		return expiresAt;
	}
}
