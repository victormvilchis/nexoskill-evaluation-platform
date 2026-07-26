package com.nexoskill.evaluation.users.application.model;

import com.nexoskill.evaluation.users.domain.model.UserStatus;
import java.time.Instant;

public record CreateUserCommand(String email, String firstName, String lastName, String displayName, String roleCode,
		String organizationPublicId, UserStatus initialStatus, Instant startsAt, Instant expiresAt, Long actorUserId,
		String ipAddress, String userAgent) {
}
