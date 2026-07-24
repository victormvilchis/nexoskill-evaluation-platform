package com.nexoskill.evaluation.authentication.application.model;

import com.nexoskill.evaluation.users.domain.model.UserAccount;
import com.nexoskill.evaluation.users.domain.model.UserAccessStatus;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

public record CurrentUser(
        String publicId,
        String email,
        String firstName,
        String lastName,
        String displayName,
        Set<String> roles,
        Set<String> permissions,
        Instant lastLoginAt,
        UserAccessStatus accessStatus,
        Instant accessStartsAt,
        Instant accessExpiresAt,
        boolean passwordChangeRequired,
        Instant passwordChangedAt,
        Instant temporaryPasswordExpiresAt
) {
    public static CurrentUser from(UserAccount user, Instant now) {
        return new CurrentUser(
                user.getPublicId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getDisplayName(),
                user.getRoles().stream()
                        .map(role -> role.code())
                        .collect(Collectors.toUnmodifiableSet()),
                user.permissions(),
                user.getLastLoginAt(),
                user.getAccess().effectiveStatusAt(now),
                user.getAccess().startsAt(),
                user.getAccess().expiresAt(),
                user.isPasswordChangeRequired(),
                user.getPasswordChangedAt(),
                user.getTemporaryPasswordExpiresAt()
        );
    }
}
