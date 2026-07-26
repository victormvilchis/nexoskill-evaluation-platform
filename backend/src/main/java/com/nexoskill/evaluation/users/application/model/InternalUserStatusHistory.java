package com.nexoskill.evaluation.users.application.model;

import com.nexoskill.evaluation.users.domain.model.UserStatus;
import java.time.Instant;

public record InternalUserStatusHistory(UserStatus previousStatus, UserStatus newStatus, String reason,
        String actorDisplayName, Instant occurredAt) {
}
