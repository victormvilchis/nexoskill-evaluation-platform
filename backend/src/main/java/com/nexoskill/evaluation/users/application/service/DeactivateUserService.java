package com.nexoskill.evaluation.users.application.service;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.users.application.model.AdminUserSummary;
import com.nexoskill.evaluation.users.application.model.UserStatusCommand;
import com.nexoskill.evaluation.users.application.port.out.UserManagementPort;
import com.nexoskill.evaluation.users.application.port.out.UserSessionPort;
import com.nexoskill.evaluation.users.domain.model.UserAccessStatus;
import com.nexoskill.evaluation.users.domain.model.UserStatus;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeactivateUserService {
    private final UserManagementPort users;
    private final UserSessionPort sessions;
    private final InternalUserTransitionPolicy transitionPolicy;
    private final InternalUserStatusHistoryService history;
    private final AuditLogPort audit;
    private final Clock clock;

    public DeactivateUserService(UserManagementPort users, UserSessionPort sessions,
            InternalUserTransitionPolicy transitionPolicy, InternalUserStatusHistoryService history,
            AuditLogPort audit, Clock clock) {
        this.users = users;
        this.sessions = sessions;
        this.transitionPolicy = transitionPolicy;
        this.history = history;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public AdminUserSummary deactivate(UserStatusCommand command) {
        var managed = users.getByPublicId(command.publicId());
        if (managed.internalId().equals(command.actorUserId())) {
            throw new BusinessException("SELF_DEACTIVATE_NOT_ALLOWED", "No puedes inactivar tu propia cuenta.");
        }
        AdminUserSummary before = managed.summary();
        transitionPolicy.validate(before.status(), UserStatus.INACTIVE);
        Instant now = clock.instant();
        if (before.status() == UserStatus.ACTIVE && before.roles().contains("ADMINISTRATOR")
                && users.countEffectiveAdministrators(now) <= 1) {
            throw new BusinessException("LAST_ADMINISTRATOR_REQUIRED",
                    "No puedes inactivar al último administrador activo de la plataforma.");
        }
        String reason = transitionPolicy.normalizeReason(command.reason(), false, "Inactivación administrativa");
        AdminUserSummary updated = users.updateStatus(command.publicId(), UserStatus.INACTIVE,
                UserAccessStatus.SUSPENDED, command.actorUserId(), reason, now);
        int revoked = sessions.revokeActiveSessions(managed.internalId(), now);
        history.record(managed.internalId(), command.actorUserId(), before.status(), UserStatus.INACTIVE, reason, now);
        var data = UserManagementSupport.auditData(before, updated);
        data.put("revokedSessions", revoked);
        audit.record(command.actorUserId(), "USER_DEACTIVATED", "USER_MANAGEMENT",
                "Se inactivó un usuario interno y se revocaron sus sesiones.", command.ipAddress(),
                command.userAgent(), data, now);
        return updated;
    }
}
