package com.nexoskill.evaluation.authentication.application.model;

import com.nexoskill.evaluation.users.domain.model.UserAccount;
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
        Instant lastLoginAt
) {
    public static CurrentUser from(UserAccount user) {
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
                user.getLastLoginAt()
        );
    }
}
