package com.nexoskill.evaluation.users.application.model;

public record OwnProfile(
        String publicId,
        String email,
        String firstName,
        String lastName,
        String displayName
) {
    public static OwnProfile from(AdminUserSummary user) {
        return new OwnProfile(
                user.publicId(),
                user.email(),
                user.firstName(),
                user.lastName(),
                user.displayName()
        );
    }
}
