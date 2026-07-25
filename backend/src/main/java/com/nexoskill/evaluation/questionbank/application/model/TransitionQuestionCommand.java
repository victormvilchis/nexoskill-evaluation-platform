package com.nexoskill.evaluation.questionbank.application.model;

import java.util.Set;

public record TransitionQuestionCommand(
        String publicId,
        String targetStatus,
        long expectedEntityVersion,
        Long actorUserId,
        Set<String> actorPermissions,
        String ipAddress,
        String userAgent
) {
    public TransitionQuestionCommand {
        actorPermissions = actorPermissions == null ? Set.of() : Set.copyOf(actorPermissions);
    }
}
