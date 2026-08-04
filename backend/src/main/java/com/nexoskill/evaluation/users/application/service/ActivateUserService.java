package com.nexoskill.evaluation.users.application.service;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.users.application.model.AdminUserSummary;
import com.nexoskill.evaluation.users.application.model.UserStatusCommand;
import com.nexoskill.evaluation.users.application.port.out.UserManagementPort;
import com.nexoskill.evaluation.users.domain.model.UserAccessStatus;
import com.nexoskill.evaluation.users.domain.model.UserStatus;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivateUserService {
	private final UserManagementPort users;
	private final InternalUserTransitionPolicy transitionPolicy;
	private final InternalUserStatusHistoryService history;
	private final AuditLogPort audit;
	private final Clock clock;

	public ActivateUserService(UserManagementPort users, InternalUserTransitionPolicy transitionPolicy,
			InternalUserStatusHistoryService history, AuditLogPort audit, Clock clock) {
		this.users = users;
		this.transitionPolicy = transitionPolicy;
		this.history = history;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional
	public AdminUserSummary activate(UserStatusCommand command) {
		var managed = users.getByPublicId(command.publicId());
		AdminUserSummary before = managed.summary();
		transitionPolicy.validate(before.status(), UserStatus.ACTIVE);
		Instant now = clock.instant();
		String reason = transitionPolicy.normalizeReason(command.reason(), false, "Reactivación administrativa");
		AdminUserSummary updated = users.updateStatus(command.publicId(), UserStatus.ACTIVE, UserAccessStatus.ACTIVE,
				command.actorUserId(), reason, now);
		history.record(managed.internalId(), command.actorUserId(), before.status(), UserStatus.ACTIVE, reason, now);
		audit.record(command.actorUserId(), "USER_ACTIVATED", "USER_MANAGEMENT", "Se activó un usuario interno.",
				command.ipAddress(), command.userAgent(), UserManagementSupport.auditData(before, updated), now);
		return updated;
	}
}
