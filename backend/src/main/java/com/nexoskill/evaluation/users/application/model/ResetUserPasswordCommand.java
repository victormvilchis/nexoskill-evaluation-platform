package com.nexoskill.evaluation.users.application.model;

public record ResetUserPasswordCommand(String publicId, String temporaryPassword, Long actorUserId, String ipAddress,
		String userAgent) {
}
