package com.nexoskill.evaluation.users.application.model;

public record UpdateUserRoleCommand(
        String publicId,
        String roleCode,
        Long actorUserId,
        String ipAddress,
        String userAgent
) {
}
