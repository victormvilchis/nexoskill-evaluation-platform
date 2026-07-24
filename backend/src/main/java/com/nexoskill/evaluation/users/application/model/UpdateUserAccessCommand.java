package com.nexoskill.evaluation.users.application.model;

import java.time.Instant;

public record UpdateUserAccessCommand(
        String publicId,
        Instant startsAt,
        Instant expiresAt,
        Long actorUserId,
        String ipAddress,
        String userAgent
) {
}
