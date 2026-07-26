package com.nexoskill.evaluation.users.application.model;

public record CreateUserResult(AdminUserSummary user, String temporaryPassword) {
}
