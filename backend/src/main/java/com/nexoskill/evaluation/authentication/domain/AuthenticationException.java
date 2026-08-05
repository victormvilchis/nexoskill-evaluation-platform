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
		return new AuthenticationException("AUTHENTICATION_FAILED", "Usuario o contraseña incorrectos.");
	}

	public static AuthenticationException roleInactive() {
		return new AuthenticationException("ROLE_INACTIVE",
				"El rol asignado a tu cuenta se encuentra inactivo. Contacta a un administrador.");
	}

	public static AuthenticationException accountInactive() {
		return new AuthenticationException("ACCOUNT_INACTIVE",
				"Tu cuenta se encuentra inactiva. Contacta a un administrador.");
	}

	public static AuthenticationException accountSuspended() {
		return new AuthenticationException("ACCOUNT_SUSPENDED",
				"Tu acceso se encuentra suspendido. Contacta a un administrador.");
	}

	public static AuthenticationException accountDeleted() {
		return new AuthenticationException("AUTHENTICATION_FAILED",
				"No fue posible iniciar sesión con las credenciales proporcionadas.");
	}

	public static AuthenticationException accountTemporarilyLocked() {
		return new AuthenticationException("ACCOUNT_TEMPORARILY_LOCKED",
				"Tu cuenta está bloqueada temporalmente por intentos fallidos. Intenta más tarde.");
	}

	public static AuthenticationException organizationInactive() {
		return new AuthenticationException("ORGANIZATION_INACTIVE",
				"La organización asociada a tu cuenta se encuentra inactiva.");
	}

	public static AuthenticationException organizationExpired() {
		return new AuthenticationException("ORGANIZATION_EXPIRED",
				"La organización asociada a tu cuenta ya no se encuentra vigente.");
	}

	public static AuthenticationException accessExpired() {
		return new AuthenticationException("ACCESS_EXPIRED", "Tu acceso a la plataforma ha expirado.");
	}

	public static AuthenticationException temporaryPasswordExpired() {
		return new AuthenticationException("TEMP_PASSWORD_EXPIRED",
				"La contraseña temporal ha expirado. Solicita un restablecimiento al administrador.");
	}

	public static AuthenticationException unauthorized() {
		return new AuthenticationException("UNAUTHORIZED", "La sesión no es válida o ha vencido.");
	}
}
