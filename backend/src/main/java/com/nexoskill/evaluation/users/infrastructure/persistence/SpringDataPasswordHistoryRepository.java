package com.nexoskill.evaluation.users.infrastructure.persistence;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataPasswordHistoryRepository
        extends JpaRepository<PasswordHistoryJpaEntity, Long> {

    List<PasswordHistoryJpaEntity> findByUserIdOrderByCreatedAtDesc(
            Long userId,
            Pageable pageable
    );
}
