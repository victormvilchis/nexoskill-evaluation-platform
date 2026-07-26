package com.nexoskill.evaluation.users.application.model;

import java.time.Instant;

public record UpdateInternalUserCommand(String publicId, String email, String firstName, String lastName,
		String displayName, String roleCode, String organizationPublicId, Instant startsAt, Instant expiresAt,
		Long actorUserId, String ipAddress, String userAgent) {
}
