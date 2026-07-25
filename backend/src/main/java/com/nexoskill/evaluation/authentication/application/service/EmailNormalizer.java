package com.nexoskill.evaluation.authentication.application.service;

import java.util.Locale;

public final class EmailNormalizer {

	private EmailNormalizer() {
	}

	public static String normalize(String email) {
		return email == null ? "" : email.trim().toUpperCase(Locale.ROOT);
	}
}
