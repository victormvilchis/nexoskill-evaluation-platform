package com.nexoskill.evaluation.authentication.domain;

public class AuthenticationException extends RuntimeException {

    private final String code;

    public AuthenticationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static AuthenticationException invalidCredentials() {
        return new AuthenticationException(
                "AUTHENTICATION_FAILED",
                "El correo o la contraseña son incorrectos."
        );
    }

    public static AuthenticationException unauthorized() {
        return new AuthenticationException(
                "UNAUTHORIZED",
                "La sesión no es válida o ha vencido."
        );
    }
}
