package com.nexoskill.evaluation.users.application.service;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.shared.domain.BusinessException;
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

	private final UserManagementPort userManagementPort;
	private final AuditLogPort auditLogPort;
	private final Clock clock;

	public ActivateUserService(UserManagementPort userManagementPort, AuditLogPort auditLogPort, Clock clock) {
		this.userManagementPort = userManagementPort;
		this.auditLogPort = auditLogPort;
		this.clock = clock;
	}

	@Transactional
	public AdminUserSummary activate(UserStatusCommand command) {
		UserManagementPort.ManagedUser managedUser = userManagementPort.getByPublicId(command.publicId());
		Instant now = clock.instant();
		AdminUserSummary before = managedUser.summary();

		if (before.expiresAt() != null && !before.expiresAt().isAfter(now)) {
			throw new BusinessException("USER_ACCESS_EXPIRED",
					"Actualiza la fecha de vencimiento antes de activar al usuario.");
		}

		AdminUserSummary updated = userManagementPort.updateStatus(command.publicId(), UserStatus.ACTIVE,
				UserAccessStatus.ACTIVE, now);

		auditLogPort.record(command.actorUserId(), "USER_ACTIVATED", "USER_MANAGEMENT", "Se activó un usuario.",
				command.ipAddress(), command.userAgent(), UserManagementSupport.auditData(before, updated), now);
		return updated;
	}
}
