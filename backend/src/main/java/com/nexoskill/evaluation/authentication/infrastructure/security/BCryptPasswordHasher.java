package com.nexoskill.evaluation.authentication.infrastructure.security;

import com.nexoskill.evaluation.authentication.application.port.out.PasswordHasher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BCryptPasswordHasher implements PasswordHasher {

	private final PasswordEncoder passwordEncoder;

	public BCryptPasswordHasher(PasswordEncoder passwordEncoder) {
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	public boolean matches(String rawPassword, String encodedPassword) {
		return passwordEncoder.matches(rawPassword, encodedPassword);
	}

	@Override
	public String encode(String rawPassword) {
		return passwordEncoder.encode(rawPassword);
	}
}
