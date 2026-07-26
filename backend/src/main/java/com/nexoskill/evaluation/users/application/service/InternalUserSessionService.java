package com.nexoskill.evaluation.users.application.service;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.users.application.model.InternalUserSessionSummary;
import com.nexoskill.evaluation.users.application.port.out.UserManagementPort;
import com.nexoskill.evaluation.users.application.port.out.UserSessionPort;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InternalUserSessionService {
    private final UserManagementPort users;
    private final UserSessionPort sessions;
    private final AuditLogPort audit;
    private final Clock clock;

    public InternalUserSessionService(UserManagementPort users, UserSessionPort sessions, AuditLogPort audit,
            Clock clock) {
        this.users = users;
        this.sessions = sessions;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<InternalUserSessionSummary> list(String publicId) {
        return sessions.listSessions(users.getByPublicId(publicId).internalId());
    }

    @Transactional
    public int revoke(String publicId, Long actorUserId, String ipAddress, String userAgent) {
        var managed = users.getByPublicId(publicId);
        var now = clock.instant();
        int revoked = sessions.revokeActiveSessions(managed.internalId(), now);
        audit.record(actorUserId, "USER_SESSIONS_REVOKED", "USER_MANAGEMENT",
                "Se revocaron manualmente las sesiones de un usuario interno.", ipAddress, userAgent,
                Map.of("targetUserPublicId", publicId, "revokedSessions", revoked), now);
        return revoked;
    }
}
