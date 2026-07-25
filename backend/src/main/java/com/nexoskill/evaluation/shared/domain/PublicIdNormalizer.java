package com.nexoskill.evaluation.shared.domain;

import java.util.UUID;

public final class PublicIdNormalizer {

	private PublicIdNormalizer() {
	}

	public static String requiredUuid(String value, String code, String message) {
		if (value == null || value.isBlank()) {
			throw new BusinessException(code, message);
		}
		String candidate = value.trim();
		try {
			String canonical = UUID.fromString(candidate).toString();
			if (!canonical.equalsIgnoreCase(candidate)) {
				throw new IllegalArgumentException("UUID no canónico");
			}
			return canonical;
		} catch (IllegalArgumentException exception) {
			throw new BusinessException("PUBLIC_ID_INVALID",
					"El identificador proporcionado no tiene un formato válido.");
		}
	}
}
