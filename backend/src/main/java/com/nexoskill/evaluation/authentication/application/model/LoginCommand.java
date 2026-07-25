package com.nexoskill.evaluation.authentication.application.model;

public record LoginCommand(String email, String password, String ipAddress, String userAgent) {
}
