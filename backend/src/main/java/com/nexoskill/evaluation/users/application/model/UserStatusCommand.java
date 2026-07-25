package com.nexoskill.evaluation.users.application.model;

public record UserStatusCommand(String publicId, Long actorUserId, String ipAddress, String userAgent) {
}
