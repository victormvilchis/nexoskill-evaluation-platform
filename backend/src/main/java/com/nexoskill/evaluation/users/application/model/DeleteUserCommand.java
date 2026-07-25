package com.nexoskill.evaluation.users.application.model;

public record DeleteUserCommand(String publicId, String reason, Long actorUserId, String ipAddress, String userAgent) {
}
