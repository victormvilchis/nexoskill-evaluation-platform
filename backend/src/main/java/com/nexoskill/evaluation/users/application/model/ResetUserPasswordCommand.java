package com.nexoskill.evaluation.users.application.model;

public record ResetUserPasswordCommand(String publicId, Long actorUserId, String ipAddress, String userAgent) {
}
