package com.nexoskill.evaluation.users.application.model;

import com.nexoskill.evaluation.authentication.domain.model.AuthSessionScope;
import com.nexoskill.evaluation.authentication.domain.model.SessionStatus;
import java.time.Instant;

public record InternalUserSessionSummary(String publicId, SessionStatus status, AuthSessionScope scope,
		String ipAddress, String userAgent, Instant createdAt, Instant lastActivityAt, Instant expiresAt,
		Instant revokedAt) {
}
