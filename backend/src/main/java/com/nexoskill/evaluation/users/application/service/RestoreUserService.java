package com.nexoskill.evaluation.users.application.service;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.users.application.model.AdminUserSummary;
import com.nexoskill.evaluation.users.application.model.UserStatusCommand;
import com.nexoskill.evaluation.users.application.port.out.UserManagementPort;
import com.nexoskill.evaluation.users.domain.model.UserStatus;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RestoreUserService {
    private final UserManagementPort users;
    private final InternalUserTransitionPolicy transitionPolicy;
    private final InternalUserStatusHistoryService history;
    private final AuditLogPort audit;
    private final Clock clock;

    public RestoreUserService(UserManagementPort users, InternalUserTransitionPolicy transitionPolicy,
            InternalUserStatusHistoryService history, AuditLogPort audit, Clock clock) {
        this.users = users;
        this.transitionPolicy = transitionPolicy;
        this.history = history;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public AdminUserSummary restore(UserStatusCommand command) {
        var managed = users.getByPublicId(command.publicId());
        AdminUserSummary before = managed.summary();
        transitionPolicy.validate(before.status(), UserStatus.INACTIVE);
        Instant now = clock.instant();
        String reason = transitionPolicy.normalizeReason(command.reason(), false, "Restauración administrativa");
        AdminUserSummary updated = users.restore(command.publicId(), command.actorUserId(), reason, now);
        history.record(managed.internalId(), command.actorUserId(), UserStatus.DELETED, UserStatus.INACTIVE, reason,
                now);
        audit.record(command.actorUserId(), "USER_RESTORED", "USER_MANAGEMENT",
                "Se restauró un usuario interno como inactivo.", command.ipAddress(), command.userAgent(),
                UserManagementSupport.auditData(before, updated), now);
        return updated;
    }
}
