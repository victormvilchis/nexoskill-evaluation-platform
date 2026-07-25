package com.nexoskill.evaluation.authentication.application.model;

import java.time.Instant;

public record LoginResult(String rawSessionToken, Instant expiresAt, CurrentUser user) {
}
