package com.nexoskill.evaluation.users.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InternalUserStatusHistoryRepository extends JpaRepository<InternalUserStatusHistoryJpaEntity, Long> {
    List<InternalUserStatusHistoryJpaEntity> findByUserIdOrderByOccurredAtDesc(Long userId);
}
