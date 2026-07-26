package com.nexoskill.evaluation.users.application.service;

import com.nexoskill.evaluation.users.application.model.InternalUserStatusHistory;
import com.nexoskill.evaluation.users.application.port.out.UserManagementPort;
import com.nexoskill.evaluation.users.domain.model.UserStatus;
import com.nexoskill.evaluation.users.infrastructure.persistence.InternalUserStatusHistoryJpaEntity;
import com.nexoskill.evaluation.users.infrastructure.persistence.InternalUserStatusHistoryRepository;
import com.nexoskill.evaluation.users.infrastructure.persistence.SpringDataUserJpaRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InternalUserStatusHistoryService {
    private final InternalUserStatusHistoryRepository historyRepository;
    private final SpringDataUserJpaRepository userRepository;
    private final UserManagementPort users;

    public InternalUserStatusHistoryService(InternalUserStatusHistoryRepository historyRepository,
            SpringDataUserJpaRepository userRepository, UserManagementPort users) {
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
        this.users = users;
    }

    public void record(Long userId, Long actorUserId, UserStatus previousStatus, UserStatus newStatus, String reason,
            Instant occurredAt) {
        historyRepository.save(InternalUserStatusHistoryJpaEntity.create(userId, actorUserId, previousStatus,
                newStatus, reason, occurredAt));
    }

    @Transactional(readOnly = true)
    public List<InternalUserStatusHistory> list(String publicId) {
        return list(users.getByPublicId(publicId).internalId());
    }

    @Transactional(readOnly = true)
    public List<InternalUserStatusHistory> list(Long userId) {
        return historyRepository.findByUserIdOrderByOccurredAtDesc(userId).stream().map(item -> {
            String actor = item.getActorUserId() == null ? "Sistema"
                    : userRepository.findById(item.getActorUserId()).map(user -> user.getDisplayName())
                            .orElse("Usuario no disponible");
            return new InternalUserStatusHistory(item.getPreviousStatus(), item.getNewStatus(), item.getReason(),
                    actor, item.getOccurredAt());
        }).toList();
    }
}
