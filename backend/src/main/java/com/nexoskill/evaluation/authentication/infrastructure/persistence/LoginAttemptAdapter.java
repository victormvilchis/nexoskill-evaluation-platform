package com.nexoskill.evaluation.authentication.infrastructure.persistence;

import com.nexoskill.evaluation.authentication.application.port.out.LoginAttemptPort;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class LoginAttemptAdapter implements LoginAttemptPort {

	private final SpringDataLoginAttemptRepository repository;

	public LoginAttemptAdapter(SpringDataLoginAttemptRepository repository) {
		this.repository = repository;
	}

	@Override
	public void record(Long userId, String attemptedEmail, boolean successful, String failureReason, String ipAddress,
			String userAgent, Instant attemptedAt) {

		repository.save(LoginAttemptJpaEntity.create(userId, attemptedEmail, successful, failureReason, ipAddress,
				userAgent, attemptedAt));
	}
}
