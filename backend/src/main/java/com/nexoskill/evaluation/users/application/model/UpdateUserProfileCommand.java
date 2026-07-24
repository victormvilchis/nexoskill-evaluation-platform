package com.nexoskill.evaluation.users.application.model;

public record UpdateUserProfileCommand(
        String publicId,
        String email,
        String firstName,
        String lastName,
        String displayName,
        Long actorUserId,
        String ipAddress,
        String userAgent
) {
}
