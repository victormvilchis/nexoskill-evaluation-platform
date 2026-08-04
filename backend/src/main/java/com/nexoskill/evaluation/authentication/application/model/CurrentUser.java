package com.nexoskill.evaluation.authentication.application.model;

import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationJpaEntity;
import com.nexoskill.evaluation.users.domain.model.UserAccount;
import com.nexoskill.evaluation.users.domain.model.UserAccessStatus;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

public record CurrentUser(String publicId, String email, String firstName, String lastName, String displayName,
		Set<String> roles, Set<String> permissions, Instant lastLoginAt, UserAccessStatus accessStatus,
		Instant accessStartsAt, Instant accessExpiresAt, boolean passwordChangeRequired, Instant passwordChangedAt,
		Instant temporaryPasswordExpiresAt) {
	public static CurrentUser from(UserAccount user, Instant now) {
		return new CurrentUser(user.getPublicId(), user.getEmail(), user.getFirstName(), user.getLastName(),
				user.getDisplayName(),
				user.getRoles().stream().map(role -> role.code()).collect(Collectors.toUnmodifiableSet()),
				user.permissions(), user.getLastLoginAt(), user.getAccess().effectiveStatusAt(now),
				user.getAccess().startsAt(), user.getAccess().expiresAt(), user.isPasswordChangeRequired(),
				user.getPasswordChangedAt(), user.getTemporaryPasswordExpiresAt());
	}
	public static CurrentUser from(UserAccount user, Instant now, OrganizationJpaEntity organization) {
		if (organization == null || organization.isGlobal()) {
			return from(user, now);
		}
		Instant startsAt = organization.getValidFrom() == null ? null
				: organization.getValidFrom().atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
		Instant expiresAt = organization.getExpiresOn() == null ? null
				: organization.getExpiresOn().plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
		return new CurrentUser(user.getPublicId(), user.getEmail(), user.getFirstName(), user.getLastName(),
				user.getDisplayName(),
				user.getRoles().stream().map(role -> role.code()).collect(Collectors.toUnmodifiableSet()),
				user.permissions(), user.getLastLoginAt(), UserAccessStatus.ACTIVE, startsAt, expiresAt,
				user.isPasswordChangeRequired(), user.getPasswordChangedAt(), user.getTemporaryPasswordExpiresAt());
	}

}
