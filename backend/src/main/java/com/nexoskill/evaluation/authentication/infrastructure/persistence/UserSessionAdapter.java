package com.nexoskill.evaluation.authentication.infrastructure.persistence;

import com.nexoskill.evaluation.authentication.domain.model.SessionStatus;
import com.nexoskill.evaluation.users.application.port.out.UserSessionPort;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class UserSessionAdapter implements UserSessionPort {

    private final SpringDataAuthSessionRepository repository;

    public UserSessionAdapter(SpringDataAuthSessionRepository repository) {
        this.repository = repository;
    }

    @Override
    public int revokeActiveSessions(Long userId, Instant revokedAt) {
        return repository.revokeActiveSessions(
                userId,
                SessionStatus.ACTIVE,
                SessionStatus.REVOKED,
                revokedAt
        );
    }
}
