package com.nexoskill.evaluation.users.application.model;

import java.time.Instant;

public record CreateUserCommand(
        String email,
        String firstName,
        String lastName,
        String displayName,
        String roleCode,
        String temporaryPassword,
        Instant startsAt,
        Instant expiresAt,
        Long actorUserId,
        String ipAddress,
        String userAgent
) {
}
