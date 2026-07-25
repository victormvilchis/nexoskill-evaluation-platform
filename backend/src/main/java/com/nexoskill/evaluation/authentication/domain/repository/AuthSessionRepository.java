package com.nexoskill.evaluation.authentication.domain.repository;

import com.nexoskill.evaluation.authentication.domain.model.AuthSession;
import java.util.Optional;

public interface AuthSessionRepository {

	AuthSession save(AuthSession session);

	Optional<AuthSession> findByTokenHash(String tokenHash);
}
