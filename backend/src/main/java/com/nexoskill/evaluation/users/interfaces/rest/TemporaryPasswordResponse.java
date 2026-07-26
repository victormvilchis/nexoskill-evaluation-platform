package com.nexoskill.evaluation.users.interfaces.rest;

import com.nexoskill.evaluation.users.application.model.AdminUserSummary;

public record TemporaryPasswordResponse(AdminUserSummary user, String temporaryPassword) {
}
