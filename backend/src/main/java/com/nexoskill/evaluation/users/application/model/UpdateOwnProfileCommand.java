package com.nexoskill.evaluation.users.application.model;

public record UpdateOwnProfileCommand(Long actorUserId, String publicId, String firstName, String lastName,
		String displayName, String ipAddress, String userAgent) {
}
