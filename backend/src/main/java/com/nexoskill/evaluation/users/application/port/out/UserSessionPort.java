package com.nexoskill.evaluation.users.application.port.out;

import java.time.Instant;

public interface UserSessionPort {

    int revokeActiveSessions(Long userId, Instant revokedAt);
}
