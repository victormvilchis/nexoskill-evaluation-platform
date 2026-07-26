package com.nexoskill.evaluation.authentication.domain;

import org.springframework.http.HttpStatus;

public class AuthenticationException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public AuthenticationException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public String getCode() { return code; }
    public HttpStatus getStatus() { return status; }

    public static AuthenticationException invalidCredentials() {
        return new AuthenticationException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED",
                "Usuario o contraseña incorrectos.");
    }

    public static AuthenticationException accountInactive() {
        return new AuthenticationException(HttpStatus.FORBIDDEN, "ACCOUNT_INACTIVE",
                "Tu cuenta se encuentra inactiva. Contacta a un administrador.");
    }

    public static AuthenticationException accountSuspended() {
        return new AuthenticationException(HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED",
                "Tu acceso se encuentra suspendido. Contacta a un administrador.");
    }

    public static AuthenticationException accountDeleted() {
        return new AuthenticationException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED",
                "No fue posible iniciar sesión con las credenciales proporcionadas.");
    }

    public static AuthenticationException accountTemporarilyLocked() {
        return new AuthenticationException(HttpStatus.FORBIDDEN, "ACCOUNT_TEMPORARILY_LOCKED",
                "Tu cuenta está bloqueada temporalmente por intentos fallidos. Intenta más tarde.");
    }

    public static AuthenticationException organizationInactive() {
        return new AuthenticationException(HttpStatus.FORBIDDEN, "ORGANIZATION_INACTIVE",
                "La organización asociada a tu cuenta se encuentra inactiva.");
    }

    public static AuthenticationException organizationExpired() {
        return new AuthenticationException(HttpStatus.FORBIDDEN, "ORGANIZATION_EXPIRED",
                "La organización asociada a tu cuenta ya no se encuentra vigente.");
    }

    public static AuthenticationException accessExpired() {
        return new AuthenticationException(HttpStatus.FORBIDDEN, "ACCESS_EXPIRED",
                "Tu acceso a la plataforma ha expirado.");
    }

    public static AuthenticationException temporaryPasswordExpired() {
        return new AuthenticationException(HttpStatus.UNAUTHORIZED, "TEMP_PASSWORD_EXPIRED",
                "La contraseña temporal ha expirado. Solicita un restablecimiento al administrador.");
    }

    public static AuthenticationException unauthorized() {
        return new AuthenticationException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                "La sesión no es válida o ha vencido.");
    }
}
