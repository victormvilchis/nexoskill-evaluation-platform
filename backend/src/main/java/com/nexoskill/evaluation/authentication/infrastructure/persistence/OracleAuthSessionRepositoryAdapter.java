package com.nexoskill.evaluation.authentication.infrastructure.persistence;

import com.nexoskill.evaluation.authentication.domain.model.AuthSession;
import com.nexoskill.evaluation.authentication.domain.repository.AuthSessionRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class OracleAuthSessionRepositoryAdapter implements AuthSessionRepository {

	private final SpringDataAuthSessionRepository repository;

	public OracleAuthSessionRepositoryAdapter(SpringDataAuthSessionRepository repository) {
		this.repository = repository;
	}

	@Override
	public AuthSession save(AuthSession session) {
		AuthSessionJpaEntity entity;
		if (session.getId() == null) {
			entity = AuthSessionJpaEntity.create(session.getPublicId(), session.getUserId(), session.getTokenHash(),
					session.getStatus(), session.getScope(), session.getIpAddress(), session.getUserAgent(),
					session.getCreatedAt(), session.getLastActivityAt(), session.getExpiresAt(),
					session.getRevokedAt());
		} else {
			entity = repository.findById(session.getId())
					.orElseThrow(() -> new IllegalStateException("Sesión no encontrada"));
			entity.apply(session.getStatus(), session.getRevokedAt());
		}
		return toDomain(repository.save(entity));
	}

	@Override
	public Optional<AuthSession> findByTokenHash(String tokenHash) {
		return repository.findByTokenHash(tokenHash).map(this::toDomain);
	}

	private AuthSession toDomain(AuthSessionJpaEntity entity) {
		return new AuthSession(entity.getId(), entity.getPublicId(), entity.getUserId(), entity.getTokenHash(),
				entity.getStatus(), entity.getScope(), entity.getIpAddress(), entity.getUserAgent(),
				entity.getCreatedAt(), entity.getLastActivityAt(), entity.getExpiresAt(), entity.getRevokedAt());
	}
}
