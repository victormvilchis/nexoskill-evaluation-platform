package com.nexoskill.evaluation.users.interfaces.rest;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record UpdateUserAccessRequest(@NotNull Instant startsAt, Instant expiresAt) {
}
