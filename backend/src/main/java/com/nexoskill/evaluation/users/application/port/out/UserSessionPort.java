package com.nexoskill.evaluation.users.application.port.out;

import com.nexoskill.evaluation.users.application.model.InternalUserSessionSummary;
import java.time.Instant;
import java.util.List;

public interface UserSessionPort {
    int revokeActiveSessions(Long userId, Instant revokedAt);
    int revokeOtherActiveSessions(Long userId, String currentTokenHash, Instant revokedAt);
    List<InternalUserSessionSummary> listSessions(Long userId);
}
