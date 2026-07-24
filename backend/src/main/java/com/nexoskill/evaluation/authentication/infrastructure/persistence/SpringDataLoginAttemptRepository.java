package com.nexoskill.evaluation.authentication.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataLoginAttemptRepository
        extends JpaRepository<LoginAttemptJpaEntity, Long> {
}
