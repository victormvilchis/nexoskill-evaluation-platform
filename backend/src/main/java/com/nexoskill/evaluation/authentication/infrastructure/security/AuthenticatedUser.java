package com.nexoskill.evaluation.authentication.infrastructure.security;

import com.nexoskill.evaluation.authentication.application.model.CurrentUser;
import java.time.Instant;
import java.util.Set;

public record AuthenticatedUser(
        Long internalId,
        String publicId,
        String email,
        String firstName,
        String lastName,
        String displayName,
        Set<String> roles,
        Set<String> permissions,
        Instant lastLoginAt
) {
    public CurrentUser toCurrentUser() {
        return new CurrentUser(
                publicId,
                email,
                firstName,
                lastName,
                displayName,
                roles,
                permissions,
                lastLoginAt
        );
    }
}
