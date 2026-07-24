package com.nexoskill.evaluation.users.application.model;

import com.nexoskill.evaluation.users.domain.model.UserAccessStatus;
import com.nexoskill.evaluation.users.domain.model.UserStatus;
import java.time.Instant;
import java.util.Set;

public record AdminUserSummary(
        String publicId,
        String email,
        String firstName,
        String lastName,
        String displayName,
        UserStatus status,
        Set<String> roles,
        UserAccessStatus accessStatus,
        Instant startsAt,
        Instant expiresAt,
        Instant lastLoginAt
) {
    public AdminUserSummary {
        roles = Set.copyOf(roles);
    }
}
