package com.nexoskill.evaluation.authentication.application.model;

public record ChangePasswordCommand(Long userId, String currentPassword, String newPassword, String confirmPassword,
		String currentSessionTokenHash, String ipAddress, String userAgent) {
}
