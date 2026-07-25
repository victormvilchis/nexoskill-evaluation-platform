package com.nexoskill.evaluation.users.application.service;

import com.nexoskill.evaluation.shared.domain.BusinessException;
import org.springframework.stereotype.Component;

@Component
public class PasswordPolicy {

	public void validate(String password, String email) {
		boolean valid = password != null && password.length() >= 10 && password.length() <= 128
				&& password.chars().anyMatch(Character::isUpperCase)
				&& password.chars().anyMatch(Character::isLowerCase) && password.chars().anyMatch(Character::isDigit)
				&& password.chars().anyMatch(value -> !Character.isLetterOrDigit(value));

		if (!valid) {
			throw new BusinessException("PASSWORD_POLICY_VIOLATION",
					"La contraseña debe tener entre 10 y 128 caracteres, mayúscula, minúscula, número y símbolo.");
		}

		String localPart = email == null ? "" : email.split("@", 2)[0];
		if (localPart.length() >= 3 && password.toLowerCase().contains(localPart.toLowerCase())) {
			throw new BusinessException("PASSWORD_CONTAINS_EMAIL",
					"La contraseña no puede contener el nombre de tu correo.");
		}
	}
}
