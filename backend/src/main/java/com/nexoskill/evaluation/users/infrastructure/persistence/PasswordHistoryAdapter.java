package com.nexoskill.evaluation.users.infrastructure.persistence;

import com.nexoskill.evaluation.users.application.port.out.PasswordHistoryPort;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
public class PasswordHistoryAdapter implements PasswordHistoryPort {

	private final SpringDataPasswordHistoryRepository repository;

	public PasswordHistoryAdapter(SpringDataPasswordHistoryRepository repository) {
		this.repository = repository;
	}

	@Override
	public List<String> recentHashes(Long userId, int limit) {
		if (limit <= 0) {
			return List.of();
		}
		return repository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, limit)).stream()
				.map(PasswordHistoryJpaEntity::getPasswordHash).toList();
	}

	@Override
	public void record(Long userId, String passwordHash, Instant createdAt) {
		repository.save(PasswordHistoryJpaEntity.create(userId, passwordHash, createdAt));
	}
}
