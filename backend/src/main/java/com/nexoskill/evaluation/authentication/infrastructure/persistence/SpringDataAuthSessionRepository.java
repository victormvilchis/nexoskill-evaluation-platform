package com.nexoskill.evaluation.authentication.infrastructure.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataAuthSessionRepository
        extends JpaRepository<AuthSessionJpaEntity, Long> {

    Optional<AuthSessionJpaEntity> findByTokenHash(String tokenHash);
}
