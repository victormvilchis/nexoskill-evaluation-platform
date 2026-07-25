package com.nexoskill.evaluation.users.application.service;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.shared.domain.BusinessException;
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
    private final AuditLogPort audit;
    private final Clock clock;

    public RestoreUserService(UserManagementPort users, AuditLogPort audit, Clock clock) {
        this.users = users; this.audit = audit; this.clock = clock;
    }

    @Transactional
    public AdminUserSummary restore(UserStatusCommand command) {
        var managed = users.getByPublicId(command.publicId());
        if (managed.summary().status() != UserStatus.DELETED) {
            throw new BusinessException("USER_NOT_DELETED", "El usuario no se encuentra eliminado.");
        }
        Instant now = clock.instant();
        AdminUserSummary updated = users.restore(command.publicId(), now);
        audit.record(command.actorUserId(), "USER_RESTORED", "USER_MANAGEMENT",
                "Se restauró un usuario como suspendido.", command.ipAddress(), command.userAgent(),
                UserManagementSupport.auditData(managed.summary(), updated), now);
        return updated;
    }
}
