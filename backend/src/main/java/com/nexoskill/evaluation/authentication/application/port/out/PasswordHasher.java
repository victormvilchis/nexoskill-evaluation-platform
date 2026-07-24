package com.nexoskill.evaluation.authentication.application.port.out;

public interface PasswordHasher {

    boolean matches(String rawPassword, String encodedPassword);

    String encode(String rawPassword);
}
