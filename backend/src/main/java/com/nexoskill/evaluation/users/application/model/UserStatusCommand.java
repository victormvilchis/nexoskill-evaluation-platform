package com.nexoskill.evaluation.users.application.model;

public record UserStatusCommand(String publicId, String reason, Long actorUserId, String ipAddress, String userAgent) {
}
