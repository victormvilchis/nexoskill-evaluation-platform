package com.nexoskill.evaluation.authentication.infrastructure.persistence;

import com.nexoskill.evaluation.authentication.domain.model.SessionStatus;
import com.nexoskill.evaluation.users.application.model.InternalUserSessionSummary;
import com.nexoskill.evaluation.users.application.port.out.UserSessionPort;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class UserSessionAdapter implements UserSessionPort {
    private final SpringDataAuthSessionRepository repository;

    public UserSessionAdapter(SpringDataAuthSessionRepository repository) {
        this.repository = repository;
    }

    @Override
    public int revokeActiveSessions(Long userId, Instant revokedAt) {
        return repository.revokeActiveSessions(userId, SessionStatus.ACTIVE, SessionStatus.REVOKED, revokedAt);
    }

    @Override
    public int revokeOtherActiveSessions(Long userId, String currentTokenHash, Instant revokedAt) {
        return repository.revokeOtherActiveSessions(userId, currentTokenHash, SessionStatus.ACTIVE,
                SessionStatus.REVOKED, revokedAt);
    }

    @Override
    public List<InternalUserSessionSummary> listSessions(Long userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(session -> new InternalUserSessionSummary(session.getPublicId(), session.getStatus(),
                        session.getScope(), session.getIpAddress(), session.getUserAgent(), session.getCreatedAt(),
                        session.getLastActivityAt(), session.getExpiresAt(), session.getRevokedAt()))
                .toList();
    }
}
