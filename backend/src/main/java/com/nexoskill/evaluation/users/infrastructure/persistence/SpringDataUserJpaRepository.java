package com.nexoskill.evaluation.users.infrastructure.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataUserJpaRepository
        extends JpaRepository<UserJpaEntity, Long> {

    Optional<UserJpaEntity> findByNormalizedEmail(String normalizedEmail);
}
